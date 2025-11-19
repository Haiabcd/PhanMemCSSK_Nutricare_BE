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
    private static final double WATER_ML_PER_KG = 35.0;

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

    private record DayPlanContext(
            ProfileCreationRequest profile,
            int weight,
            List<NutritionRule> rules,
            AggregateConstraints constraints,
            Nutrition dayTarget,
            double waterTargetMl
    ) {}

    private DayPlanContext buildDayPlanContext(MealPlanCreationRequest request) {
        ProfileCreationRequest profile = request.getProfile();
        int weight = Math.max(1, profile.getWeightKg());

        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(request.getUserId());
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);

        // Day target theo logic cũ
        Nutrition dayTarget = caculateNutrition(profile, agg);

        // Water target theo logic cũ
        double waterMl = weight * WATER_ML_PER_KG;
        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }

        return new DayPlanContext(profile, weight, rules, agg, dayTarget, waterMl);
    }

    @Override
    public MealPlanResponse createPlan(MealPlanCreationRequest request, int number) {
        DayPlanContext ctx = buildDayPlanContext(request);

        UUID userId = request.getUserId();
        Nutrition target = ctx.dayTarget();
        double waterMl = ctx.waterTargetMl();
        int weight = ctx.weight();
        List<NutritionRule> rules = ctx.rules();

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
                if (!globalTagDir.getPreferBonus().isEmpty()) {
                    long cnt = tagsOf(f).stream()
                            .filter(globalTagDir.getPreferBonus()::containsKey)
                            .count();
                    s += cnt * 0.8;
                }
                if (!globalTagDir.getLimitPenalty().isEmpty()) {
                    long cnt = tagsOf(f).stream()
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
        // Khoảng cách còn thiếu (chỉ 5 chất chính)
        java.util.function.Function<Nutrition, Double> dist = (rem) -> {
            double wK  = 1.0 / Math.max(1.0, EPS_KCAL);
            double wP  = 1.0 / Math.max(1.0, EPS_PROT);
            double wC  = 1.0 / Math.max(1.0, EPS_CARB);
            double wF  = 1.0 / Math.max(1.0, EPS_FAT);
            double wFi = 2.0 / Math.max(1.0, EPS_FIBER);

            return wK  * Math.abs(safeDouble(rem.getKcal()))
                    + wP  * Math.abs(safeDouble(rem.getProteinG()))
                    + wC  * Math.abs(safeDouble(rem.getCarbG()))
                    + wF  * Math.abs(safeDouble(rem.getFatG()))
                    + wFi * Math.abs(safeDouble(rem.getFiberG()));
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
                while (picked < itemCount && !isSatisfiedSlot(remaining, slotTarget) && scanGuard < pool.size() * 3) {
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
                            Nutrition afterRem = subNutSigned(remaining, snap);
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

                    if (bestFood == null || bestGain <= 0) break;
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
                    remaining = subNutSigned(remaining, bestSnap);
                    picked++;
                }
                if (!isSatisfiedSlot(remaining, slotTarget) && picked < itemCount) {
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
                            Nutrition afterRem = subNutSigned(remaining, snap);
                            double after = dist.apply(afterRem);
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
        for (MealPlanDay day : savedDays) {
            postTuneDayForFatAndFiber(day, target, rules, request);
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
        // ===== 1) Lấy profile =====
        ProfileCreationRequest profile = profileOrchestrator.getByUserId_request(userId);
        MealPlanCreationRequest req = MealPlanCreationRequest.builder()
                .userId(userId)
                .profile(profile)
                .build();

        // Dùng context chung
        DayPlanContext ctx = buildDayPlanContext(req);
        Nutrition dayTarget = ctx.dayTarget();
        List<NutritionRule> rules = ctx.rules();
        int weight = ctx.weight();

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
        // ===== 1) Profile + rules + day target =====
        ProfileCreationRequest pReq = profileOrchestrator.getByUserId_request(userId);
        MealPlanCreationRequest mReq =
                MealPlanCreationRequest.builder().userId(userId).profile(pReq).build();

        DayPlanContext ctx = buildDayPlanContext(mReq);
        Nutrition dayTarget = ctx.dayTarget();
        double waterMl = ctx.waterTargetMl();
        int weight = ctx.weight();
        List<NutritionRule> rules = ctx.rules();

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
        final int NO_REPEAT_FUTURE_DAYS = 2;
        LocalDate startRecent = date.minusDays(NO_REPEAT_DAYS);
        LocalDate endRecent   = date.plusDays(NO_REPEAT_FUTURE_DAYS);

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
                mealPlanItemRepository.findDistinctFoodIdsPlannedBetween(userId, startRecent, endRecent);
        recentFoods.addAll(plannedRecently);
        // ===== 4) Xóa item cũ chưa dùng (tránh FK & rác) =====
        mealPlanItemRepository.deleteUnusedItemsByDay(day.getId());
        // ===== 5) Tag directives để lọc avoid/limit/prefer =====
        TagDirectives tagDir = buildTagDirectives(rules, mReq);
        int rankBase = 1
                + mealPlanItemRepository
                .findByDay_User_IdAndDay_Date(userId, date)
                .size();
        // ===== 6) Helper local cho chọn theo vector =====

        // Khoảng cách còn thiếu (L1, trọng số) cho 5 chất chính
        java.util.function.Function<Nutrition, Double> dist = (rem) -> {
            double wK  = 1.0 / Math.max(1.0, EPS_KCAL);
            double wP  = 1.0 / Math.max(1.0, EPS_PROT);
            double wC  = 1.0 / Math.max(1.0, EPS_CARB);
            double wF  = 1.0 / Math.max(1.0, EPS_FAT);
            double wFi = 2.0 / Math.max(1.0, EPS_FIBER);
            return wK  * Math.abs(safeDouble(rem.getKcal()))
                    + wP  * Math.abs(safeDouble(rem.getProteinG()))
                    + wC  * Math.abs(safeDouble(rem.getCarbG()))
                    + wF  * Math.abs(safeDouble(rem.getFatG()))
                    + wFi * Math.abs(safeDouble(rem.getFiberG()));
        };

        // ===== 7) Với từng slot: tính target MEAL → remaining → chọn theo gain vector =====
        for (MealSlot slot : MealSlot.values()) {
            int targetItems = SLOT_ITEM_COUNTS.get(slot);
            double pct = SLOT_KCAL_PCT.get(slot);
            // 7.1) Meal target & remaining
            Nutrition mealTarget = approxMacroTargetForMeal(dayTarget, pct, rules, weight, mReq);
            Nutrition consumed = consumedBySlot.getOrDefault(slot, new Nutrition());
            Nutrition remaining = subNutSigned(mealTarget, consumed);
            if (isSatisfiedSlot(remaining, mealTarget)) continue;
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

            while (picked < need && !isSatisfiedSlot(remaining, mealTarget) && scanGuard < candidates.size() * 3) {
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
                        Nutrition afterRem = subNutSigned(remaining, snap);
                        double after = dist.apply(afterRem);
                        double gain = (before - after) + 0.10 * scoreFoodHeuristic(cand, slotTargetRemaining);

                        if (gain > bestGain) {
                            bestGain = gain;
                            bestFood = cand;
                            bestPortion = portion;
                            bestSnap = snap;
                        }
                    }
                }

                if (bestFood == null || bestGain <= 0) break;

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
                remaining = subNutSigned(remaining, bestSnap);

                picked++;
            }

            // 7.7) Fallback bù thêm 1 item nếu vẫn chưa “đủ gần”
            if (!isSatisfiedSlot(remaining, mealTarget) && picked < need) {
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
                        Nutrition afterRem = subNutSigned(remaining, snap);
                        double after = dist.apply(afterRem);
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
        postTuneDayForFatAndFiber(day, dayTarget, rules, mReq);
        mealPlanItemRepository.flush();
        entityManager.flush();
        entityManager.clear();

        MealPlanDay hydrated =
                mealPlanDayRepository.findByUser_IdAndDate(userId, date).orElse(day);
        return mealPlanDayMapper.toMealPlanResponse(hydrated, cdnHelper);
    }

    /* ===================== HÀM PHỤ TRỢ ===================== */
    private Nutrition subNutSigned(Nutrition target, Nutrition consumed) {
        double kcal   = safeDouble(target.getKcal())     - safeDouble(consumed.getKcal());
        double prot   = safeDouble(target.getProteinG()) - safeDouble(consumed.getProteinG());
        double carb   = safeDouble(target.getCarbG())    - safeDouble(consumed.getCarbG());
        double fat    = safeDouble(target.getFatG())     - safeDouble(consumed.getFatG());
        double fiber  = safeDouble(target.getFiberG())   - safeDouble(consumed.getFiberG());
        double sodium = safeDouble(target.getSodiumMg()) - safeDouble(consumed.getSodiumMg());
        double sugar  = safeDouble(target.getSugarMg())  - safeDouble(consumed.getSugarMg());

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
        double extraProtPenalty = 0.0;
        if (tp > 0) {
            double overRatio = p / (tp + 1e-6);
            if (overRatio > 1.1) {
                extraProtPenalty = (overRatio - 1.1) * 3.0;
            }
        }

        return -ratioPenalty
                + (kcalDensityScore / 300.0)
                + (fiberBonus * 0.2)
                - sodiumPenalty
                - sugarPenalty
                - extraProtPenalty;
    }


    // Cộng tổng dinh dưỡng của list item
    private Nutrition sumNutrition(List<MealPlanItem> items) {
        Nutrition total = new Nutrition();
        for (MealPlanItem i : items) {
            total = addNut(total, i.getNutrition());
        }
        return total;
    }

    // Tuning cuối ngày: nếu thiếu fat/xơ thì bù thêm 1 món SNACK
    // Tuning cuối ngày: nếu thiếu fat/xơ thì bù thêm 1 món SNACK
    private void postTuneDayForFatAndFiber(
            MealPlanDay day,
            Nutrition dayTarget,
            List<NutritionRule> rules,
            MealPlanCreationRequest request
    ) {
        // 1) Lấy tất cả item của ngày
        List<MealPlanItem> items = mealPlanItemRepository.findByDay_Id(day.getId());
        if (items.isEmpty()) return;

        Nutrition actual = sumNutrition(items);

        double tK  = safeDouble(dayTarget.getKcal());
        double tP  = safeDouble(dayTarget.getProteinG());
        double tF  = safeDouble(dayTarget.getFatG());
        double tFi = safeDouble(dayTarget.getFiberG());

        double aK  = safeDouble(actual.getKcal());
        double aP  = safeDouble(actual.getProteinG());
        double aF  = safeDouble(actual.getFatG());
        double aFi = safeDouble(actual.getFiberG());

        if (tK <= 0 || tF <= 0 || tFi <= 0) return;

        double kcalRatio  = aK  / tK;
        double fatRatio   = aF  / tF;
        double fiberRatio = aFi / tFi;

        // Nếu kcal đã vượt max → không bù nữa
        if (kcalRatio > KCAL_MAX_RATIO) return;

        // Chỉ bù khi fat hoặc fiber còn thiếu khá nhiều
        boolean needFat   = fatRatio   < FAT_MIN_RATIO;
        boolean needFiber = fiberRatio < FIBER_MIN_RATIO;

        if (!needFat && !needFiber) return;

        // 2) Ước lượng ngân sách kcal để bù (tối đa ~10% target, nhưng không dưới 50)
        int extraKcalBudget = (int) Math.round(Math.min(200, tK * 0.10));
        if (extraKcalBudget < 50) extraKcalBudget = 50;

        // 3) Lấy pool ứng viên SNACK theo cửa sổ kcal
        final int CANDIDATE_LIMIT = 60;
        int minKcal = 50;
        int maxKcal = Math.max(minKcal + 10, extraKcalBudget);

        List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                MealSlot.SNACK.name(), minKcal, maxKcal, extraKcalBudget, CANDIDATE_LIMIT);
        if (pool == null || pool.isEmpty()) return;

        // 4) Lọc theo tag avoid / limit từ rule
        TagDirectives tagDir = buildTagDirectives(rules, request);

        List<Food> candidates = pool.stream()
                .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Né trùng món đã có trong ngày
        Set<UUID> usedFoodIds = items.stream()
                .filter(i -> i.getFood() != null)
                .map(i -> i.getFood().getId())
                .collect(Collectors.toSet());

        candidates = candidates.stream()
                .filter(f -> !usedFoodIds.contains(f.getId()))
                .collect(Collectors.toCollection(ArrayList::new));

        if (candidates.isEmpty()) return;

        // 5) Chọn 1 món có fiber/fat tốt, protein không quá cao
        Food best = null;
        double bestScore = -1e9;
        Nutrition bestNut = null;
        double bestPortion = 1.0;

        for (Food cand : candidates) {
            Nutrition nutBase = cand.getNutrition();
            if (nutBase == null || nutBase.getKcal() == null) continue;

            for (double portion : PORTION_STEPS) {
                Nutrition snap = scaleNutrition(nutBase, portion);
                if (!passesItemRules(rules, snap, request)) continue;

                double k  = safeDouble(snap.getKcal());
                double p  = safeDouble(snap.getProteinG());
                double f  = safeDouble(snap.getFatG());
                double fi = safeDouble(snap.getFiberG());

                if (k <= 0 || k > extraKcalBudget * 1.5) continue;

                // 🚫 Không cho vượt ngưỡng kcal & protein sau khi bù
                double newKcalRatio = (aK + k) / tK;
                if (newKcalRatio > KCAL_MAX_RATIO) continue;

                if (tP > 0) {
                    double newProtRatio = (aP + p) / tP;
                    if (newProtRatio > PROT_MAX_RATIO) continue;
                }

                // Ưu tiên: nhiều fiber/fat, ít protein
                double score = 0.0;
                if (needFiber) score += fi * 2.0;   // đẩy xơ
                if (needFat)   score += f  * 1.5;   // đẩy béo

                // phạt đạm cao
                score -= p * 1.5;   // tăng phạt đạm một chút

                if (score > bestScore) {
                    bestScore = score;
                    best = cand;
                    bestNut = snap;
                    bestPortion = portion;
                }
            }
        }

        if (best == null || bestNut == null) return;

        // 6) Thêm món bù vào SNACK, rank = maxRank + 1
        int maxRank = items.stream()
                .mapToInt(MealPlanItem::getRank)
                .max()
                .orElse(0);

        mealPlanItemRepository.save(MealPlanItem.builder()
                .day(day)
                .mealSlot(MealSlot.SNACK)
                .food(best)
                .portion(bd(bestPortion, 2))
                .used(false)
                .rank(maxRank + 1)
                .nutrition(bestNut)
                .build());
    }


}
