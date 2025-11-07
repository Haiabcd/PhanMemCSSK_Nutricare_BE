package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import static com.hn.nutricarebe.helper.PlanLogHelper.resolveActualOrFallback;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.DayTarget;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.MealPlanDayMapper;
import com.hn.nutricarebe.orchestrator.ProfileOrchestrator;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.NutritionRuleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;

@Log4j2
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanDayServiceImpl implements MealPlanDayService {
    MealPlanDayRepository mealPlanDayRepository;
    MealPlanDayMapper mealPlanDayMapper;
    ProfileOrchestrator profileOrchestrator;
    NutritionRuleService nutritionRuleService;
    FoodRepository foodRepository;
    MealPlanItemRepository mealPlanItemRepository;
    PlanLogRepository planLogRepository;
    CdnHelper cdnHelper;

    @PersistenceContext
    EntityManager entityManager;

    @Override
    public MealPlanResponse createPlan(MealPlanCreationRequest request, int number) {
        final double WATER_ML_PER_KG = 35.0;

        /* ================== 1. PROFILE + RULES + RÀNG BUỘC ================== */
        var profile = request.getProfile();
        int weight = Math.max(1, profile.getWeightKg());

        UUID userId = request.getUserId();
        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);

        /* ================== 2. TÍNH MỤC TIÊU NƯỚC ================== */
        double waterMl = weight * WATER_ML_PER_KG;
        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }

        /* ================== 3. TÍNH MỤC TIÊU DINH DƯỠNG NGÀY ================== */
        Nutrition target = caculateNutrition(request.getProfile(), agg);

        /* ================== 4. TẠO CÁC NGÀY TRONG KẾ HOẠCH ================== */
        LocalDate startDate = LocalDate.now();
        List<MealPlanDay> days = new ArrayList<>(number);
        User user = User.builder().id(userId).build();
        for (int i = 0; i < number; i++) {
            LocalDate d = startDate.plusDays(i);
            days.add(MealPlanDay.builder()
                    .user(user)
                    .targetNutrition(target)
                    .date(d)
                    .waterTargetMl((int) Math.round(waterMl))
                    .build());
        }
        List<MealPlanDay> savedDays = mealPlanDayRepository.saveAll(days);

        int totalItemsPerDay =
                SLOT_ITEM_COUNTS.values().stream().mapToInt(Integer::intValue).sum();

        /* ================== 6. CHUẨN BỊ POOL ỨNG VIÊN CHO TỪNG SLOT ================== */
        record SlotPool(List<Food> foods, Map<UUID, Double> baseScore) {}
        Map<MealSlot, SlotPool> pools = new EnumMap<>(MealSlot.class);

        final int CANDIDATE_LIMIT = 80;
        final int noRepeatWindow = 3; // Không cho lặp món trong 3 ngày bất kể bữa
        final long seed = Objects.hash(userId, LocalDate.now().get(WEEK_OF_WEEK_BASED_YEAR));
        Random rng = new Random(seed);

        double dayTargetKcal = safeDouble(target.getKcal());
        TagDirectives globalTagDir = buildTagDirectives(rules, request);

        for (MealSlot slot : MealSlot.values()) {
            double slotKcal = dayTargetKcal * SLOT_KCAL_PCT.get(slot);
            int itemCount = SLOT_ITEM_COUNTS.get(slot);
            int perItem = (int) Math.round(slotKcal / Math.max(1, itemCount));

            // ---- Tìm ứng viên theo kcal cửa sổ ----
            List<Food> pool = new ArrayList<>();
            double lowMul = 0.5, highMul = 2.0;
            for (int attempt = 0; attempt < 5; attempt++) {
                int minKcal = Math.max(20, (int) Math.round(perItem * lowMul));
                int maxKcal = Math.max(minKcal + 10, (int) Math.round(perItem * highMul));
                pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                        slot.name(), minKcal, maxKcal, perItem, CANDIDATE_LIMIT);
                if (pool != null && pool.size() >= itemCount) break;
                lowMul *= 0.7;
                highMul *= 1.3;
            }
            if (pool == null) pool = Collections.emptyList();

            // ---- Lọc món AVOID theo rule ----
            pool = pool.stream()
                    .filter(f -> Collections.disjoint(tagsOf(f), globalTagDir.getAvoid()))
                    .collect(Collectors.toCollection(ArrayList::new));

            // ---- Tính điểm heuristic + prefer/limit ----
            Nutrition slotTarget = approxMacroTargetForMeal(target, SLOT_KCAL_PCT.get(slot), rules, weight, request);
            Map<UUID, Double> score = new HashMap<>();
            for (Food f : pool) {
                double s = scoreFoodHeuristic(f, slotTarget);
                s += scoreFoodByLLMIfAny(f, slot, slotTarget);
                if (!globalTagDir.getPreferBonus().isEmpty()) {
                    long cnt = f.getTags().stream()
                            .filter(globalTagDir.getPreferBonus()::containsKey)
                            .count();
                    s += cnt * 0.8;
                }
                if (!globalTagDir.getLimitPenalty().isEmpty()) {
                    long cnt = f.getTags().stream()
                            .filter(globalTagDir.getLimitPenalty()::containsKey)
                            .count();
                    s -= cnt * 0.7;
                }
                score.put(f.getId(), s);
            }

            // ---- Sắp xếp theo điểm và xáo nhẹ để đa dạng ----
            pool.sort(Comparator.<Food>comparingDouble(f -> score.getOrDefault(f.getId(), 0.0))
                    .reversed());
            for (int i = 0; i + 4 < pool.size(); i += 5) {
                Collections.shuffle(pool.subList(i, i + 5), rng);
            }
            pools.put(slot, new SlotPool(pool, score));
        }

        /* ================== 7. KHỞI TẠO HÀNG ĐỢI CHỐNG TRÙNG CHUNG ================== */
        Deque<UUID> recentAll = new ArrayDeque<>();

        /* ================== 8. GHÉP MÓN CHO TỪNG NGÀY (VECTOR-AWARE) ================== */

        // Hàm phụ trong phạm vi method: kiểm đủ gần cho SLOT (scale EPS theo %kcal slot)
        java.util.function.BiPredicate<Nutrition, Double> isSatisfiedSlot = (rem, pct) -> {
            double scale = Math.max(0.6, pct * 1.2); // nới nhẹ theo % slot
            return safeDouble(rem.getKcal()) <= Math.max(20.0, EPS_KCAL * scale)
                    && safeDouble(rem.getProteinG()) <= Math.max(1.0, EPS_PROT * scale)
                    && safeDouble(rem.getCarbG()) <= Math.max(2.0, EPS_CARB * scale)
                    && safeDouble(rem.getFatG()) <= Math.max(1.0, EPS_FAT * scale)
                    && safeDouble(rem.getFiberG()) <= Math.max(1.0, EPS_FIBER * scale);
            // Sodium/Sugar giữ nguyên (không ép “đủ”), được kiểm soát bởi passesItemRules & slotTarget meal.
        };

        // Khoảng cách còn thiếu (chỉ 5 chất chính)
        java.util.function.Function<Nutrition, Double> dist = (rem) -> {
            double wK = 1.0 / Math.max(1.0, EPS_KCAL);
            double wP = 1.0 / Math.max(1.0, EPS_PROT);
            double wC = 1.0 / Math.max(1.0, EPS_CARB);
            double wF = 1.0 / Math.max(1.0, EPS_FAT);
            double wFi = 1.2 / Math.max(1.0, EPS_FIBER);
            return wK * Math.max(0, safeDouble(rem.getKcal()))
                    + wP * Math.max(0, safeDouble(rem.getProteinG()))
                    + wC * Math.max(0, safeDouble(rem.getCarbG()))
                    + wF * Math.max(0, safeDouble(rem.getFatG()))
                    + wFi * Math.max(0, safeDouble(rem.getFiberG()));
        };

        for (MealPlanDay day : savedDays) {
            int rank = 1;

            for (MealSlot slot : MealSlot.values()) {
                double pct = SLOT_KCAL_PCT.get(slot);
                int itemCount = SLOT_ITEM_COUNTS.get(slot);

                SlotPool sp = pools.get(slot);
                List<Food> pool = sp.foods();
                if (pool.isEmpty()) continue;

                // Target của bữa + remaining vector
                Nutrition slotTarget = approxMacroTargetForMeal(target, pct, rules, weight, request);
                Nutrition remaining = Nutrition.builder()
                        .kcal(bd(safeDouble(slotTarget.getKcal()), 2))
                        .proteinG(bd(safeDouble(slotTarget.getProteinG()), 2))
                        .carbG(bd(safeDouble(slotTarget.getCarbG()), 2))
                        .fatG(bd(safeDouble(slotTarget.getFatG()), 2))
                        .fiberG(bd(safeDouble(slotTarget.getFiberG()), 2))
                        .sodiumMg(bd(safeDouble(slotTarget.getSodiumMg()), 2))
                        .sugarMg(bd(safeDouble(slotTarget.getSugarMg()), 2))
                        .build();

                int picked = 0;
                Set<UUID> usedThisSlot = new HashSet<>();
                int scanGuard = 0;

                // Vòng chọn chính: greedy theo gain (giảm khoảng cách vector)
                while (picked < itemCount && !isSatisfiedSlot.test(remaining, pct) && scanGuard < pool.size() * 3) {
                    scanGuard++;

                    double bestGain = -1e9;
                    Food bestFood = null;
                    double bestPortion = 1.0;
                    Nutrition bestSnap = null;

                    for (Food cand : pool) {
                        if (usedThisSlot.contains(cand.getId())) continue;
                        if (recentAll.contains(cand.getId())) continue;

                        var nut = cand.getNutrition();
                        if (nut == null || nut.getKcal() == null || safeDouble(nut.getKcal()) <= 0) continue;

                        for (double portion : PORTION_STEPS) {
                            Nutrition snap = scaleNutrition(nut, portion);

                            // Giữ nguyên kiểm soát sodium/sugar qua rule:
                            if (!passesItemRules(rules, snap, request)) {
                                // thử stepDown
                                var step = stepDown(portion);
                                boolean fixed = false;
                                while (step.isPresent()) {
                                    double p2 = step.getAsDouble();
                                    Nutrition s2 = scaleNutrition(nut, p2);
                                    if (passesItemRules(rules, s2, request)) {
                                        portion = p2;
                                        snap = s2;
                                        fixed = true;
                                        break;
                                    }
                                    step = stepDown(p2);
                                }
                                if (!fixed) continue;
                            }

                            // Tính gain: giảm khoảng cách vector (5 chất chính)
                            double before = dist.apply(remaining);
                            Nutrition afterRem = subNutClamp0(remaining, snap);
                            double after = dist.apply(afterRem);
                            double gain = (before - after);

                            // đa dạng + phù hợp composition
                            gain += 0.10 * scoreFoodHeuristic(cand, slotTarget);

                            if (gain > bestGain) {
                                bestGain = gain;
                                bestFood = cand;
                                bestPortion = portion;
                                bestSnap = snap;
                            }
                        }
                    }

                    if (bestFood == null || bestGain <= 0) break; // không cải thiện thêm

                    // Lưu item tốt nhất
                    mealPlanItemRepository.save(MealPlanItem.builder()
                            .day(day)
                            .mealSlot(slot)
                            .food(bestFood)
                            .portion(bd(bestPortion, 2))
                            .used(false)
                            .rank(rank++)
                            .nutrition(bestSnap)
                            .build());

                    // cập nhật hàng đợi chống trùng
                    recentAll.addLast(bestFood.getId());
                    while (recentAll.size() > noRepeatWindow * totalItemsPerDay) recentAll.removeFirst();

                    usedThisSlot.add(bestFood.getId());
                    remaining = subNutClamp0(remaining, bestSnap);
                    picked++;
                }

                // Fallback vector-aware: nếu vẫn chưa “đủ gần”, thử bù thêm trong biên 1 món
                if (!isSatisfiedSlot.test(remaining, pct) && picked < itemCount) {
                    // thử thêm 1 pick nữa theo đúng gain logic
                    double bestGain = -1e9;
                    Food bestFood = null;
                    double bestPortion = 1.0;
                    Nutrition bestSnap = null;

                    for (Food cand : pool) {
                        if (usedThisSlot.contains(cand.getId())) continue;
                        if (recentAll.contains(cand.getId())) continue;

                        var nut = cand.getNutrition();
                        if (nut == null || nut.getKcal() == null || safeDouble(nut.getKcal()) <= 0) continue;

                        // ước lượng mục tiêu per-item dựa trên remaining kcal
                        for (double portion : PORTION_STEPS) {
                            Nutrition snap = scaleNutrition(nut, portion);
                            if (!passesItemRules(rules, snap, request)) {
                                var step = stepDown(portion);
                                boolean fixed = false;
                                while (step.isPresent()) {
                                    double p2 = step.getAsDouble();
                                    Nutrition s2 = scaleNutrition(nut, p2);
                                    if (passesItemRules(rules, s2, request)) {
                                        portion = p2;
                                        snap = s2;
                                        fixed = true;
                                        break;
                                    }
                                    step = stepDown(p2);
                                }
                                if (!fixed) continue;
                            }
                            double before = dist.apply(remaining);
                            double after = dist.apply(subNutClamp0(remaining, snap));
                            double gain = (before - after) + 0.10 * scoreFoodHeuristic(cand, slotTarget);

                            if (gain > bestGain) {
                                bestGain = gain;
                                bestFood = cand;
                                bestPortion = portion;
                                bestSnap = snap;
                            }
                        }
                    }

                    if (bestFood != null && bestGain > 0) {
                        mealPlanItemRepository.save(MealPlanItem.builder()
                                .day(day)
                                .mealSlot(slot)
                                .food(bestFood)
                                .portion(bd(bestPortion, 2))
                                .used(false)
                                .rank(rank++)
                                .nutrition(bestSnap)
                                .build());
                        recentAll.addLast(bestFood.getId());
                        while (recentAll.size() > noRepeatWindow * totalItemsPerDay) recentAll.removeFirst();
                    }
                }
            }
        }

        /* ================== 9. TRẢ VỀ KẾ HOẠCH CỦA NGÀY ĐẦU TIÊN ================== */
        return mealPlanDayMapper.toMealPlanResponse(savedDays.getFirst(), cdnHelper);
    }

    @Override
    @Transactional
    public MealPlanResponse getMealPlanByDate(LocalDate date) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        MealPlanDay m = mealPlanDayRepository.findByUser_IdAndDate(userId, date).orElse(null);

        if (m == null) {
            return createOrUpdatePlanForOneDay(date, userId);
        }
        return mealPlanDayMapper.toMealPlanResponse(m, cdnHelper);
    }

    @Override
    @Transactional
    public void removeFromDate(LocalDate today, UUID userId) {
        mealPlanItemRepository.deleteItemsFromDate(userId, today);
        mealPlanDayRepository.deleteFromDate(userId, today);
    }

    @Override
    @Transactional
    public MealPlanResponse updatePlanForOneDay(LocalDate date, UUID userId) {
        return createOrUpdatePlanForOneDay(date, userId);
    }

    @Override
    @Transactional
    public double getMealTargetKcal(UUID userId, MealSlot slot) {
        // ===== 1) Lấy profile + rules =====
        ProfileCreationRequest profile = profileOrchestrator.getByUserId_request(userId);
        int weight = Math.max(1, profile.getWeightKg());
        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);
        // ===== 2) Tính target dinh dưỡng ngày =====
        MealPlanCreationRequest req = MealPlanCreationRequest.builder()
                .userId(userId)
                .profile(profile)
                .build();
        Nutrition dayTarget = caculateNutrition(req.getProfile(), agg);
        // ===== 3) Xác định % kcal theo slot =====
        Map<MealSlot, Double> slotKcalPct = Map.of(
                MealSlot.BREAKFAST, 0.25,
                MealSlot.LUNCH, 0.30,
                MealSlot.DINNER, 0.30,
                MealSlot.SNACK, 0.15);
        double pct = slotKcalPct.getOrDefault(slot, 0.0);
        if (pct <= 0) return 0.0;
        // ===== 4) Tính target dinh dưỡng cho bữa đó =====
        Nutrition mealTarget = approxMacroTargetForMeal(dayTarget, pct, rules, weight, req);
        // ===== 5) Trả về kcal mục tiêu của bữa =====
        return safeDouble(mealTarget.getKcal());
    }

    @Override
    @Transactional
    public List<DayTarget> getDayTargetsBetween(LocalDate from, LocalDate to, UUID userId) {
        List<MealPlanDay> days = mealPlanDayRepository.findByUser_IdAndDateBetweenOrderByDateAsc(userId, from, to);
        return days.stream()
                .map(d -> new DayTarget(d.getDate(), d.getTargetNutrition()))
                .toList();
    }

    public MealPlanResponse createOrUpdatePlanForOneDay(LocalDate date, UUID userId) {
        final double WATER_ML_PER_KG = 35.0;
        // ===== 1) Profile + rules + day target =====
        ProfileCreationRequest pReq = profileOrchestrator.getByUserId_request(userId);

        int weight = Math.max(1, pReq.getWeightKg());
        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);

        MealPlanCreationRequest mReq =
                MealPlanCreationRequest.builder().userId(userId).profile(pReq).build();

        Nutrition dayTarget = caculateNutrition(mReq.getProfile(), agg);

        double waterMl = weight * WATER_ML_PER_KG;
        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }

        // ===== 2) Lấy/ tạo MealPlanDay cho date =====
        MealPlanDay day = mealPlanDayRepository
                .findByUser_IdAndDate(userId, date)
                .orElseGet(() -> MealPlanDay.builder()
                        .user(User.builder().id(userId).build())
                        .date(date)
                        .build());
        day.setTargetNutrition(dayTarget);
        day.setWaterTargetMl((int) Math.round(waterMl));
        day = mealPlanDayRepository.save(day);

        // ===== 3) Đọc log hôm nay + gần đây (để né món và tính consumed) =====
        final int NO_REPEAT_DAYS = 3;
        LocalDate startRecent = date.minusDays(NO_REPEAT_DAYS);

        List<PlanLog> todayLogs = planLogRepository.findByUser_IdAndDate(userId, date);
        List<PlanLog> recentLogs =
                planLogRepository.findByUser_IdAndDateBetween(userId, startRecent, date.minusDays(1));

        // 3.1) Consumed per slot (vector dinh dưỡng)
        Map<MealSlot, Nutrition> consumedBySlot = new EnumMap<>(MealSlot.class);
        for (MealSlot s : MealSlot.values()) consumedBySlot.put(s, new Nutrition());

        Set<UUID> eatenFoodToday = new HashSet<>();
        for (PlanLog l : todayLogs) {
            MealSlot s = l.getMealSlot();
            Nutrition add = resolveActualOrFallback(l);
            consumedBySlot.put(s, addNut(consumedBySlot.get(s), add));
            if (l.getFood() != null) eatenFoodToday.add(l.getFood().getId());
        }

        // 3.2) Né món gần đây (kể cả hôm nay)
        Set<UUID> recentFoods = recentLogs.stream()
                .filter(l -> l.getFood() != null)
                .map(l -> l.getFood().getId())
                .collect(Collectors.toSet());
        recentFoods.addAll(eatenFoodToday);

        Set<UUID> plannedRecently =
                mealPlanItemRepository.findDistinctFoodIdsPlannedBetween(userId, startRecent, date.minusDays(1));
        recentFoods.addAll(plannedRecently);

        // ===== 4) Xóa item cũ chưa dùng (tránh FK & rác) =====
        mealPlanItemRepository.deleteUnusedItemsByDay(day.getId());

        // ===== 5) Tag directives để lọc avoid/limit/prefer =====
        TagDirectives tagDir = buildTagDirectives(rules, mReq);

        // số thứ tự tiếp theo cho rank
        int rankBase = 1
                + mealPlanItemRepository
                        .findByDay_User_IdAndDay_Date(userId, date)
                        .size();

        // ===== 6) Helper local cho chọn theo vector =====
        // Kiểm đủ gần theo % kcal của slot (chỉ xét kcal, protein, carb, fat, fiber)
        java.util.function.BiPredicate<Nutrition, Double> isSatisfiedSlot = (rem, pct) -> {
            double scale = Math.max(0.6, pct * 1.2); // nới nhẹ theo tỷ trọng slot
            return safeDouble(rem.getKcal()) <= Math.max(20.0, EPS_KCAL * scale)
                    && safeDouble(rem.getProteinG()) <= Math.max(1.0, EPS_PROT * scale)
                    && safeDouble(rem.getCarbG()) <= Math.max(2.0, EPS_CARB * scale)
                    && safeDouble(rem.getFatG()) <= Math.max(1.0, EPS_FAT * scale)
                    && safeDouble(rem.getFiberG()) <= Math.max(1.0, EPS_FIBER * scale);
            // Sodium/Sugar: giữ nguyên cách kiểm soát cũ qua rule/target, không ép “điền đủ”.
        };

        // Khoảng cách còn thiếu (L1, trọng số) cho 5 chất chính
        java.util.function.Function<Nutrition, Double> dist = (rem) -> {
            double wK = 1.0 / Math.max(1.0, EPS_KCAL);
            double wP = 1.0 / Math.max(1.0, EPS_PROT);
            double wC = 1.0 / Math.max(1.0, EPS_CARB);
            double wF = 1.0 / Math.max(1.0, EPS_FAT);
            double wFi = 1.2 / Math.max(1.0, EPS_FIBER);
            return wK * Math.max(0, safeDouble(rem.getKcal()))
                    + wP * Math.max(0, safeDouble(rem.getProteinG()))
                    + wC * Math.max(0, safeDouble(rem.getCarbG()))
                    + wF * Math.max(0, safeDouble(rem.getFatG()))
                    + wFi * Math.max(0, safeDouble(rem.getFiberG()));
        };

        // ===== 7) Với từng slot: tính target MEAL → remaining → chọn theo gain vector =====
        for (MealSlot slot : MealSlot.values()) {
            int targetItems = SLOT_ITEM_COUNTS.get(slot);
            double pct = SLOT_KCAL_PCT.get(slot);

            // 7.1) Meal target & remaining
            Nutrition mealTarget = approxMacroTargetForMeal(dayTarget, pct, rules, weight, mReq);
            Nutrition consumed = consumedBySlot.getOrDefault(slot, new Nutrition());
            Nutrition remaining = subNutClamp0(mealTarget, consumed);
            if (isSatisfiedSlot.test(remaining, pct)) continue;

            // 7.2) Pool ứng viên theo slot
            final int CANDIDATE_LIMIT = 120;
            final int MIN_KCAL = 20, MAX_KCAL = 2000, PIVOT = 500;
            List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                    slot.name(), MIN_KCAL, MAX_KCAL, PIVOT, CANDIDATE_LIMIT);
            if (pool == null) pool = Collections.emptyList();

            // 7.3) Lọc AVOID + né recentFoods
            List<Food> candidates = pool.stream()
                    .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                    .filter(f -> !recentFoods.contains(f.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));

            // 7.4) Sắp xếp “gợi ý” (không bắt buộc) theo heuristic hiện có
            Nutrition slotTargetRemaining = remaining;
            candidates.sort(Comparator.comparingDouble((Food f) -> scoreFoodHeuristic(f, slotTargetRemaining))
                    .reversed());

            // 7.5) Ước lượng số món cần, nhưng thực tế dừng theo “đủ gần” vector
            double slotQuotaKcal = safeDouble(dayTarget.getKcal()) * pct;
            double rKcal = safeDouble(remaining.getKcal());
            int need = estimateItemNeed(slot, rKcal, slotQuotaKcal, targetItems);
            if (need <= 0) continue;

            // 7.6) Vòng chọn: greedy theo gain giảm khoảng cách vector
            int picked = 0;
            Set<UUID> usedThisSlot = new HashSet<>();
            int scanGuard = 0;

            while (picked < need && !isSatisfiedSlot.test(remaining, pct) && scanGuard < candidates.size() * 3) {
                scanGuard++;

                double bestGain = -1e9;
                Food bestFood = null;
                double bestPortion = 1.0;
                Nutrition bestSnap = null;

                for (Food cand : candidates) {
                    if (usedThisSlot.contains(cand.getId())) continue;

                    var nut = cand.getNutrition();
                    if (nut == null || nut.getKcal() == null || safeDouble(nut.getKcal()) <= 0) continue;

                    for (double portion : PORTION_STEPS) {
                        Nutrition snap = scaleNutrition(nut, portion);

                        // Sodium/Sugar & các rule item-level giữ nguyên cách kiểm soát cũ:
                        if (!passesItemRules(rules, snap, mReq)) {
                            var step = stepDown(portion);
                            boolean fixed = false;
                            while (step.isPresent()) {
                                double p2 = step.getAsDouble();
                                Nutrition s2 = scaleNutrition(nut, p2);
                                if (passesItemRules(rules, s2, mReq)) {
                                    portion = p2;
                                    snap = s2;
                                    fixed = true;
                                    break;
                                }
                                step = stepDown(p2);
                            }
                            if (!fixed) continue;
                        }

                        // Gain = giảm khoảng cách vector (5 chất chính)
                        double before = dist.apply(remaining);
                        double after = dist.apply(subNutClamp0(remaining, snap));
                        double gain = (before - after) + 0.10 * scoreFoodHeuristic(cand, slotTargetRemaining);

                        if (gain > bestGain) {
                            bestGain = gain;
                            bestFood = cand;
                            bestPortion = portion;
                            bestSnap = snap;
                        }
                    }
                }

                if (bestFood == null || bestGain <= 0) break; // không cải thiện thêm

                // Lưu item
                mealPlanItemRepository.save(MealPlanItem.builder()
                        .day(day)
                        .mealSlot(slot)
                        .food(bestFood)
                        .portion(bd(bestPortion, 2))
                        .used(false)
                        .rank(rankBase++)
                        .nutrition(bestSnap)
                        .build());

                usedThisSlot.add(bestFood.getId());
                recentFoods.add(bestFood.getId());

                // Trừ remaining theo vector
                remaining = subNutClamp0(remaining, bestSnap);

                picked++;
            }

            // 7.7) Fallback bù thêm 1 item nếu vẫn chưa “đủ gần”
            if (!isSatisfiedSlot.test(remaining, pct) && picked < need) {
                double bestGain = -1e9;
                Food bestFood = null;
                double bestPortion = 1.0;
                Nutrition bestSnap = null;

                for (Food cand : candidates) {
                    if (usedThisSlot.contains(cand.getId())) continue;

                    var nut = cand.getNutrition();
                    if (nut == null || nut.getKcal() == null || safeDouble(nut.getKcal()) <= 0) continue;

                    for (double portion : PORTION_STEPS) {
                        Nutrition snap = scaleNutrition(nut, portion);
                        if (!passesItemRules(rules, snap, mReq)) {
                            var step = stepDown(portion);
                            boolean fixed = false;
                            while (step.isPresent()) {
                                double p2 = step.getAsDouble();
                                Nutrition s2 = scaleNutrition(nut, p2);
                                if (passesItemRules(rules, s2, mReq)) {
                                    portion = p2;
                                    snap = s2;
                                    fixed = true;
                                    break;
                                }
                                step = stepDown(p2);
                            }
                            if (!fixed) continue;
                        }
                        double before = dist.apply(remaining);
                        double after = dist.apply(subNutClamp0(remaining, snap));
                        double gain = (before - after) + 0.10 * scoreFoodHeuristic(cand, slotTargetRemaining);

                        if (gain > bestGain) {
                            bestGain = gain;
                            bestFood = cand;
                            bestPortion = portion;
                            bestSnap = snap;
                        }
                    }
                }

                if (bestFood != null && bestGain > 0) {
                    mealPlanItemRepository.save(MealPlanItem.builder()
                            .day(day)
                            .mealSlot(slot)
                            .food(bestFood)
                            .portion(bd(bestPortion, 2))
                            .used(false)
                            .rank(rankBase++)
                            .nutrition(bestSnap)
                            .build());
                    recentFoods.add(bestFood.getId());
                }
            }
        }

        // ===== 8) Trả về response =====
        mealPlanItemRepository.flush();
        entityManager.flush();
        entityManager.clear();

        MealPlanDay hydrated =
                mealPlanDayRepository.findByUser_IdAndDate(userId, date).orElse(day);
        return mealPlanDayMapper.toMealPlanResponse(hydrated, cdnHelper);
    }

    /* ===================== HÀM PHỤ TRỢ ===================== */
    private static final double EPS_KCAL = 40.0;
    private static final double EPS_PROT = 3.0;
    private static final double EPS_CARB = 6.0;
    private static final double EPS_FAT = 3.0;
    private static final double EPS_FIBER = 3.0;

    // remaining = max(0, target - consumed)
    private Nutrition subNutClamp0(Nutrition target, Nutrition consumed) {
        double kcal = Math.max(0, safeDouble(target.getKcal()) - safeDouble(consumed.getKcal()));
        double prot = Math.max(0, safeDouble(target.getProteinG()) - safeDouble(consumed.getProteinG()));
        double carb = Math.max(0, safeDouble(target.getCarbG()) - safeDouble(consumed.getCarbG()));
        double fat = Math.max(0, safeDouble(target.getFatG()) - safeDouble(consumed.getFatG()));
        double fiber = Math.max(0, safeDouble(target.getFiberG()) - safeDouble(consumed.getFiberG()));
        double sodium = Math.max(0, safeDouble(target.getSodiumMg()) - safeDouble(consumed.getSodiumMg()));
        double sugar = Math.max(0, safeDouble(target.getSugarMg()) - safeDouble(consumed.getSugarMg()));

        return Nutrition.builder()
                .kcal(bd(kcal, 2))
                .proteinG(bd(prot, 2))
                .carbG(bd(carb, 2))
                .fatG(bd(fat, 2))
                .fiberG(bd(fiber, 2))
                .sodiumMg(bd(sodium, 2))
                .sugarMg(bd(sugar, 2))
                .build();
    }

    // Ước lượng cần mấy món dựa trên kcal còn thiếu + “khung” số món mặc định
    private int estimateItemNeed(MealSlot slot, double rKcal, double slotQuotaKcal, int targetItems) {
        if (rKcal <= EPS_KCAL) return 0;
        int maxBySlot = (slot == MealSlot.SNACK ? 1 : targetItems);
        if (rKcal < 0.33 * slotQuotaKcal) return Math.min(1, maxBySlot);
        if (rKcal < 0.66 * slotQuotaKcal) return Math.min(2, maxBySlot);
        return maxBySlot;
    }

    private Nutrition approxMacroTargetForMeal(
            Nutrition dayTarget,
            double pctKcal,
            List<NutritionRule> rules,
            int weightKg,
            MealPlanCreationRequest request) {
        double kcal = safeDouble(dayTarget.getKcal()) * pctKcal;
        double ratio = kcal / Math.max(1, safeDouble(dayTarget.getKcal()));

        BigDecimal p = bd(safeDouble(dayTarget.getProteinG()) * ratio, 2);
        BigDecimal c = bd(safeDouble(dayTarget.getCarbG()) * ratio, 2);
        BigDecimal f = bd(safeDouble(dayTarget.getFatG()) * ratio, 2);
        BigDecimal fi = bd(Math.max(6.0, safeDouble(dayTarget.getFiberG()) * ratio), 2);
        BigDecimal na = bd(Math.min(700, 2000 * pctKcal), 2);
        BigDecimal su = bd(Math.max(0, safeDouble(dayTarget.getSugarMg()) * ratio), 2);

        Nutrition targetMeal = Nutrition.builder()
                .kcal(bd(kcal, 2))
                .proteinG(p)
                .carbG(c)
                .fatG(f)
                .fiberG(fi)
                .sodiumMg(na)
                .sugarMg(su)
                .build();

        AggregateConstraints a = new AggregateConstraints();
        if (rules != null && !rules.isEmpty()) {
            for (NutritionRule r : rules) {
                if (r.getScope() != RuleScope.MEAL) continue;
                if (r.getTargetType() != TargetType.NUTRIENT) continue;
                if (r.getComparator() == null) continue;
                if (!isApplicableToDemographics(r, request)) continue;

                String code = safeStr(r.getTargetCode()).toUpperCase();
                BigDecimal min = r.getThresholdMin();
                BigDecimal max = r.getThresholdMax();

                // Quy đổi perKg → gram/day
                if (Boolean.TRUE.equals(r.getPerKg())) {
                    if (min != null) min = min.multiply(BigDecimal.valueOf(weightKg));
                    if (max != null) max = max.multiply(BigDecimal.valueOf(weightKg));
                }

                switch (code) {
                    case "PROTEIN" -> applyBoundsToPair(
                            r.getComparator(),
                            min,
                            max,
                            v -> a.dayProteinMin = maxOf(a.dayProteinMin, v),
                            v -> a.dayProteinMax = minOf(a.dayProteinMax, v));

                    case "CARB" -> applyBoundsToPair(
                            r.getComparator(),
                            min,
                            max,
                            v -> a.dayCarbMin = maxOf(a.dayCarbMin, v),
                            v -> a.dayCarbMax = minOf(a.dayCarbMax, v));

                    case "FAT" -> applyBoundsToPair(
                            r.getComparator(),
                            min,
                            max,
                            v -> a.dayFatMin = maxOf(a.dayFatMin, v),
                            v -> a.dayFatMax = minOf(a.dayFatMax, v));

                    case "FIBER" -> applyBoundsToPair(
                            r.getComparator(),
                            min,
                            max,
                            v -> a.dayFiberMin = maxOf(a.dayFiberMin, v),
                            v -> a.dayFiberMax = minOf(a.dayFiberMax, v));

                    case "SODIUM" -> applyBoundsToPair(
                            r.getComparator(), min, max, v -> {}, v -> a.daySodiumMax = minOf(a.daySodiumMax, v));

                    case "SUGAR" -> applyBoundsToPair(
                            r.getComparator(), min, max, v -> {}, v -> a.daySugarMax = minOf(a.daySugarMax, v));

                    case "WATER" -> applyBoundsToPair(
                            r.getComparator(), min, max, v -> a.dayWaterMin = maxOf(a.dayWaterMin, v), v -> {
                                /* thường không giới hạn trên với nước */
                            });

                    default -> {
                        /* bỏ qua nutrient không hỗ trợ */
                    }
                }
            }
        }

        return applyAggregateConstraintsToDayTarget(targetMeal, a);
    }

    /* ===== LỌC MÓN + TÍNH ĐIỂM ===== */
    private double scoreFoodHeuristic(Food f, Nutrition slotTarget) {
        Nutrition n = f.getNutrition();
        if (n == null) return -1e9;

        double kcal = safeDouble(n.getKcal());

        double p = safeDouble(n.getProteinG());
        double c = safeDouble(n.getCarbG());
        double fat = safeDouble(n.getFatG());
        double sum = p + c + fat + 1e-6;

        double fiber = safeDouble(n.getFiberG());

        double sodium = safeDouble(n.getSodiumMg());
        double sugarMg = safeDouble(n.getSugarMg());

        double tp = safeDouble(slotTarget.getProteinG());
        double tc = safeDouble(slotTarget.getCarbG());
        double tf = safeDouble(slotTarget.getFatG());
        double sumT = tp + tc + tf + 1e-6;

        double rp = p / sum;
        double rc = c / sum;
        double rf = fat / sum;

        double rtp = (tp / sumT);
        double rtc = (tc / sumT);
        double rtf = (tf / sumT);

        double ratioPenalty = Math.abs(rp - rtp) + Math.abs(rc - rtc) + Math.abs(rf - rtf);

        double kcalDensityScore = -Math.abs(kcal - (safeDouble(slotTarget.getKcal()) / 2.5));

        double fiberBonus = Math.min(fiber, 8.0);

        double sodiumPenalty = 0.0;
        if (safeDouble(slotTarget.getSodiumMg()) > 0)
            sodiumPenalty = sodium / (safeDouble(slotTarget.getSodiumMg()) + 1e-6) * 2.0;

        double sugarPenalty = 0.0;
        if (slotTarget.getSugarMg() != null && slotTarget.getSugarMg().doubleValue() > 0) {
            sugarPenalty = sugarMg / (slotTarget.getSugarMg().doubleValue() + 1e-6) * 1.5;
        }

        return -ratioPenalty + (kcalDensityScore / 300.0) + (fiberBonus * 0.2) - sodiumPenalty - sugarPenalty;
    }

    private double scoreFoodByLLMIfAny(Food food, MealSlot slot, Nutrition slotTarget) {
        return 0.0;
    }
}
