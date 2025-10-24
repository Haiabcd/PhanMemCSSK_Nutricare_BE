package com.hn.nutricarebe.service.impl;

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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;
import static com.hn.nutricarebe.helper.MealPlanHelper.*;
import static com.hn.nutricarebe.helper.PlanLogHelper.resolveActualOrFallback;
import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;
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
        final double WATER_ML_PER_KG = 35.0;      // 30–35 ml/kg

        var profile = request.getProfile();
        int weight = Math.max(1, profile.getWeightKg());

        double waterMl = weight * WATER_ML_PER_KG;
        /* ===================== NẠP BỆNH NỀN, DỊ ỨNG & RULES ===================== */
        UUID userId = request.getUserId();
        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);

        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }

        Nutrition target = caculateNutrition(request,agg);
        /* ===================== LẬP KẾ HOẠCH CHO NUMBER NGÀY ===================== */
        LocalDate startDate = LocalDate.now();
        List<MealPlanDay> days = new ArrayList<>(number);
        User user = User.builder().id(request.getUserId()).build();
        for (int i = 0; i < number; i++) {
            LocalDate d = startDate.plusDays(i);

            MealPlanDay day = MealPlanDay.builder()
                    .user(user)
                    .targetNutrition(target)
                    .date(d)
                    .waterTargetMl((int) Math.round(waterMl))
                    .build();
            days.add(day);
        }
        List<MealPlanDay> savedDays  = mealPlanDayRepository.saveAll(days);



        /* ===================== CẤU HÌNH BỮA & SỐ MÓN/BỮA ===================== */
        Map<MealSlot, Double> slotKcalPct = Map.of(
                MealSlot.BREAKFAST, 0.25,
                MealSlot.LUNCH,     0.30,
                MealSlot.DINNER,    0.30,
                MealSlot.SNACK,     0.15
        );
        Map<MealSlot, Integer> slotItemCounts = Map.of(
                MealSlot.BREAKFAST, 2,
                MealSlot.LUNCH,     3,
                MealSlot.DINNER,    3,
                MealSlot.SNACK,     1
        );

        /* ===================== CHUẨN BỊ POOL ỨNG VIÊN ===================== */
        record SlotPool(List<Food> foods, Map<UUID, Double> baseScore) {}
        Map<MealSlot, SlotPool> pools = new EnumMap<>(MealSlot.class);

        final int CANDIDATE_LIMIT = 80;
        final int noRepeatWindow  = 3; //Ngăn lặp lại món trong 3 ngày liên tiếp để đảm bảo đa dạng.
        final long seed = Objects.hash(request.getUserId(), LocalDate.now().get(WEEK_OF_WEEK_BASED_YEAR));
        Random rng = new Random(seed);

        double dayTargetKcal = safeDouble(target.getKcal());

        for (MealSlot slot : MealSlot.values()) {
            double slotKcal = dayTargetKcal * slotKcalPct.get(slot);  //Tính sô kcal mục tiêu mỗi bữa
            int itemCount   = slotItemCounts.get(slot);               //Số món mỗi bữa
            int perItem     = (int)Math.round(slotKcal / Math.max(1, itemCount));  //Kcal mục tiêu mỗi món

            List<Food> pool = new ArrayList<>();
            double lowMul  = 0.5, highMul = 2.0;

            for (int attempt = 0; attempt < 5; attempt++) {
                int minKcal = Math.max(20, (int)Math.round(perItem * lowMul));
                int maxKcal = Math.max(minKcal + 10, (int)Math.round(perItem * highMul));

                pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                        slot.name(), minKcal, maxKcal, perItem, CANDIDATE_LIMIT
                );

                if (pool != null && pool.size() >= itemCount) break;

                lowMul  = Math.max(0.10, lowMul * 0.70);
                highMul = Math.min(4.00, highMul * 1.30);
            }

            if (pool == null) pool = Collections.emptyList();

            // Lọc món bị cấm
            TagDirectives tagDir = buildTagDirectives(rules, request);
            pool = pool.stream()
                    .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                    .collect(Collectors.toCollection(ArrayList::new)); // MUTABLE


            // Tính target nutriton bữa
            Nutrition slotTarget = approxMacroTargetForMeal(target, slotKcalPct.get(slot), rules, weight, request);

            Map<UUID, Double> score = new HashMap<>();  //Mảng điểm của từng món
            final double PREFER_BONUS_PER_TAG = 0.8;  // + điểm cho mỗi tag PREFER trùng
            final double LIMIT_PENALTY_PER_TAG = 0.7; // - điểm cho mỗi tag LIMIT trùng

            for (Food f : pool) {
                double s = scoreFoodHeuristic(f, slotTarget);
                s += scoreFoodByLLMIfAny(f, slot, slotTarget);

                Set<FoodTag> ft = tagsOf(f);

                // PREFER: cộng mềm theo số tag khớp
                if (!tagDir.getPreferBonus().isEmpty()) {
                    long cnt = ft.stream().filter(tagDir.getPreferBonus()::containsKey).count();  //Đếm số tag trùng
                    if (cnt > 0) s += cnt * PREFER_BONUS_PER_TAG;
                }
                // LIMIT: trừ mềm theo số tag khớp
                if (!tagDir.getLimitPenalty().isEmpty()) {
                    long cnt = ft.stream().filter(tagDir.getLimitPenalty()::containsKey).count();
                    if (cnt > 0) s -= cnt * LIMIT_PENALTY_PER_TAG;
                }
                score.put(f.getId(), s);
            }

            // Sắp xếp giảm dần theo điểm
            pool.sort(Comparator.<Food>comparingDouble(
                    f -> score.getOrDefault(f.getId(), 0.0)).reversed());



            // Xáo nhẹ nhóm 5 món để tăng đa dạng
            for (int i=0; i+4<pool.size(); i+=5)
                Collections.shuffle(pool.subList(i,i+5), rng);

            pools.put(slot, new SlotPool(pool, score));
        }

        // Không lặp món theo slot trong noRepeatWindow ngày
        Map<MealSlot, Deque<UUID>> recentBySlot = new EnumMap<>(MealSlot.class);
        for (MealSlot s : MealSlot.values())
            recentBySlot.put(s, new ArrayDeque<>());

        // Lập lịch 7 ngày
        for (int di = 0; di < savedDays.size(); di++) {
            MealPlanDay day = savedDays.get(di);
            int rank = 1;

            Nutrition dayTarget = day.getTargetNutrition();

            for (MealSlot slot : MealSlot.values()) {
                double slotKcal = safeDouble(dayTarget.getKcal()) * slotKcalPct.get(slot);  // Tính sô kcal mục tiêu mỗi bữa
                int itemCount   = slotItemCounts.get(slot);               // Số món mỗi bữa

                SlotPool sp = pools.get(slot);
                List<Food> pool = sp.foods();
                if (pool.isEmpty()) continue;

                // con trỏ khác nhau theo ngày để đa dạng
                int startIdx = (int)Math.floor(
                        Math.abs(rng.nextGaussian()) * 3 + di*itemCount) % Math.max(1,pool.size());

                Set<FoodTag> usedTags = new HashSet<>();
                double kcalRemain = slotKcal;  /// calo còn lại của bữa

                int picked = 0;   // Số món đã chọn
                int scan = 0;     // Số lần quét qua pool

                //Điều kiện dừng: Đủ món HOẶC đã quét pool 2 lần mà không tìm đủ
                while (picked < itemCount && scan < pool.size()*2) {
                    //Chiến thuật vòng tròn:  startIdx = 5 → lần lượt chọn món 5,6,7,8,...
                    Food cand = pool.get( (startIdx + scan) % pool.size() );
                    scan++;

                    // Kiểm tra món đã dùng trong 3 ngày gần nhất (cùng buổi)
                    if (recentBySlot.get(slot).contains(cand.getId())) continue;

                    // Lấy dinh dưỡng của món
                    var nut = cand.getNutrition();
                    // Nếu món không có dinh dưỡng loại này hoặc kcal ≤0 → bỏ qua
                    if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;

                    // Nếu món có tag trùng với tag đã dùng → 30% bỏ qua
                    if (!Collections.disjoint(usedTags, cand.getTags())) {
                        if (rng.nextDouble() < 0.30) continue;
                    }

                    // Dựa vào số kcal còn lại của bữa để tính khẩu phần món
                    double foodKcal = safeDouble(nut.getKcal());
                    double portion = pickPortionStep(kcalRemain, foodKcal);

                    if (portion <= 0.5 && scan < pool.size() && picked < itemCount - 1) {
                        continue;
                    }
                    // Tính dinh dưỡng món theo khẩu phần
                    Nutrition snap = scaleNutrition(nut, portion);


                    if (!passesItemRules(rules, cand, snap, request)) {
                        OptionalDouble nextStep = stepDown(portion);
                        if (nextStep.isEmpty()) {
                            continue;
                        }
                        double tryPortion2 = nextStep.getAsDouble();
                        Nutrition trySnap2 = scaleNutrition(nut, tryPortion2);
                        if (!passesItemRules(rules, cand, trySnap2, request)) {
                            boolean fixed = false;
                            OptionalDouble step = stepDown(tryPortion2);
                            while (step.isPresent()) {
                                double p2 = step.getAsDouble();
                                Nutrition s2 = scaleNutrition(nut, p2);
                                if (passesItemRules(rules, cand, s2, request)) {
                                    tryPortion2 = p2;
                                    trySnap2 = s2;
                                    fixed = true;
                                    break;
                                }
                                step = stepDown(p2);
                            }
                            if (!fixed) continue;
                        }
                        portion = tryPortion2;
                        snap = trySnap2;


                    }
                    // Save món đã pass
                    mealPlanItemRepository.save(MealPlanItem.builder()
                            .day(day)
                            .mealSlot(slot)
                            .food(cand)
                            .portion(bd(portion,2))
                            .used(false)
                            .rank(rank++)
                            .nutrition(snap)
                            .build());

                    // cập nhật trạng thái
                    Deque<UUID> dq = recentBySlot.get(slot);
                    dq.addLast(cand.getId());
                    while (dq.size() > noRepeatWindow * slotItemCounts.get(slot))
                        dq.removeFirst();

                    usedTags.addAll(cand.getTags());
                    kcalRemain = Math.max(0, kcalRemain - safeDouble(snap.getKcal()));
                    picked++;
                }

                // nếu vẫn thiếu món -> lấp món kcal thấp
                if (picked < itemCount) {
                    for (Food f : pool.stream()
                            .sorted(Comparator.comparingDouble(ff -> {
                                var n = ff.getNutrition();
                                return (n == null) ? Double.MAX_VALUE : safeDouble(n.getKcal());
                            }))
                            .collect(Collectors.toList())){
                        if (picked >= itemCount) break;
                        if (recentBySlot.get(slot).contains(f.getId())) continue;
                        var nut = f.getNutrition();
                        if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;
                        Nutrition snap = scaleNutrition(nut, 1.0);

                        // Kiểm tra lại dinh dưỡng
                        if (!passesItemRules(rules, f, snap, request)) continue;

                        mealPlanItemRepository.save(MealPlanItem.builder()
                                .day(day)
                                .mealSlot(slot)
                                .food(f)
                                .portion(bd(1.0,2))
                                .used(false)
                                .rank(rank++)
                                .nutrition(snap)
                                .build());
                        Deque<UUID> dq = recentBySlot.get(slot);
                        dq.addLast(f.getId());
                        while (dq.size() > noRepeatWindow * slotItemCounts.get(slot)) dq.removeFirst();
                        picked++;
                    }
                }
            }
        }

        // Trả về ngày đầu
        return mealPlanDayMapper.toMealPlanResponse(savedDays.getFirst(), cdnHelper);
    }

    @Override
    @Transactional
    public MealPlanResponse getMealPlanByDate(LocalDate date) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        MealPlanDay m = mealPlanDayRepository.findByUser_IdAndDate(userId, date)
                .orElse(null);

        if(m == null) {
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
        return  createOrUpdatePlanForOneDay(date, userId);
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
        Nutrition dayTarget = caculateNutrition(req, agg);
        // ===== 3) Xác định % kcal theo slot =====
        Map<MealSlot, Double> slotKcalPct = Map.of(
                MealSlot.BREAKFAST, 0.25,
                MealSlot.LUNCH,     0.30,
                MealSlot.DINNER,    0.30,
                MealSlot.SNACK,     0.15
        );
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
        List<MealPlanDay> days = mealPlanDayRepository
                .findByUser_IdAndDateBetweenOrderByDateAsc(userId, from, to);
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

        MealPlanCreationRequest mReq = MealPlanCreationRequest.builder()
                .userId(userId)
                .profile(pReq)
                .build();

        Nutrition dayTarget = caculateNutrition(mReq, agg);

        double waterMl = weight * WATER_ML_PER_KG;
        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }

        // ===== 2) Lấy/ tạo MealPlanDay cho date =====
        MealPlanDay day = mealPlanDayRepository.findByUser_IdAndDate(userId, date)
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

        List<PlanLog> todayLogs  = planLogRepository.findByUser_IdAndDate(userId, date);  //Lấy log ngày hôm nay
        List<PlanLog> recentLogs = planLogRepository.findByUser_IdAndDateBetween(userId, startRecent, date.minusDays(1)); //Lấy log vài ngày gần đây

        // 3.1) Consumed per slot (vector dinh dưỡng)
        Map<MealSlot, Nutrition> consumedBySlot = new EnumMap<>(MealSlot.class);
        for (MealSlot s : MealSlot.values()) consumedBySlot.put(s, new Nutrition()); // khởi tạo rỗng

        Set<UUID> eatenFoodToday = new HashSet<>();  //Món đã ăn theo ke hoach
        for (PlanLog l : todayLogs) {
            MealSlot s = l.getMealSlot();
            Nutrition add = resolveActualOrFallback(l);  //Lấy dinh dinh dưỡng đã ăn
            consumedBySlot.put(s, addNut(consumedBySlot.get(s), add));
            if (l.getFood() != null) eatenFoodToday.add(l.getFood().getId());
        }

        // 3.2) Né món gần đây (kể cả hôm nay)
        Set<UUID> recentFoods = recentLogs.stream()
                .filter(l -> l.getFood()!=null)
                .map(l -> l.getFood().getId())
                .collect(Collectors.toSet());
        recentFoods.addAll(eatenFoodToday);

        Set<UUID> plannedRecently = mealPlanItemRepository
                .findDistinctFoodIdsPlannedBetween(userId, startRecent, date.minusDays(1));
        recentFoods.addAll(plannedRecently);

        // ===== 4) Xóa item cũ chưa dùng (tránh FK & rác) =====
        mealPlanItemRepository.deleteUnusedItemsByDay(day.getId());

        // ===== 5) Tag directives để lọc avoid/limit/prefer =====
        TagDirectives tagDir = buildTagDirectives(rules, mReq);

        // ===== 6) Tham số slot (số món & %kcal) =====
        Map<MealSlot, Double> slotKcalPct = Map.of(
                MealSlot.BREAKFAST, 0.25,
                MealSlot.LUNCH,     0.30,
                MealSlot.DINNER,    0.30,
                MealSlot.SNACK,     0.15
        );
        Map<MealSlot, Integer> slotItemCounts = Map.of(
                MealSlot.BREAKFAST, 2,
                MealSlot.LUNCH,     3,
                MealSlot.DINNER,    3,
                MealSlot.SNACK,     1
        );

        // số thứ tự tiếp theo cho rank
        int rankBase = 1 + mealPlanItemRepository.findByDay_User_IdAndDay_Date(userId, date).size();

        // ===== 7) Cho từng slot: tính target MEAL → trừ consumed → còn bao nhiêu thì bổ sung =====
        for (MealSlot slot : MealSlot.values()) {
            int targetItems = slotItemCounts.get(slot);

            // 7.1) Meal target (vector) và remaining = max(0, target - consumed)
            Nutrition mealTarget = approxMacroTargetForMeal(dayTarget, slotKcalPct.get(slot), rules, weight, mReq);
            Nutrition consumed   = consumedBySlot.getOrDefault(slot, new Nutrition());  // Tổng dinh dưỡng đã ăn
            Nutrition remaining  = subNutClamp0(mealTarget, consumed); // Phần còn thiếu cần bổ sung

            // Nếu đã thừa (consumed > target) thì remaining = 0 → không bổ sung
            if (isSatisfied(remaining)) continue;

            // 7.2) Pool ứng viên theo slot
            final int CANDIDATE_LIMIT = 120;
            final int MIN_KCAL = 20, MAX_KCAL = 2000, PIVOT = 500;
            List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                    slot.name(), MIN_KCAL, MAX_KCAL, PIVOT, CANDIDATE_LIMIT
            );
            if (pool == null) pool = Collections.emptyList();

            // 7.3) Lọc AVOID + né recentFoods
            List<Food> candidates = pool.stream()
                    .filter(f -> Collections.disjoint(tagsOf(f), tagDir.getAvoid()))
                    .filter(f -> !recentFoods.contains(f.getId()))
                    .collect(Collectors.toCollection(ArrayList::new));

            // 7.4) Sắp xếp theo độ “hợp” với phần còn thiếu của slot
            Nutrition slotTargetRemaining = remaining;
            candidates.sort(Comparator.<Food>comparingDouble(
                    f -> -scoreFoodHeuristic(f, slotTargetRemaining)));

            // 7.5) Quyết định số món cần bổ sung dựa trên remaining kcal (linh hoạt)
            double slotQuotaKcal = safeDouble(dayTarget.getKcal()) * slotKcalPct.get(slot); // quota kcal của bữa
            double rKcal = safeDouble(remaining.getKcal());                                  // kcal còn thiếu của bữa
            int need = estimateItemNeed(slot, rKcal, slotQuotaKcal, targetItems);
            if (need <= 0) continue;

            // 7.6) Chọn món, update remaining (vector) sau mỗi lần pick
            int picked = 0;
            Set<UUID> usedThisSlot = new HashSet<>();

            double remainingKcal = safeDouble(remaining.getKcal());
            for (Food cand : candidates) {
                if (picked >= need) break;
                if (usedThisSlot.contains(cand.getId())) continue;

                var nut = cand.getNutrition();
                if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;

                int remainSlots = Math.max(1, need - picked);
                double perItemAimKcal = Math.max(60, remainingKcal / remainSlots);
                double portion = pickPortionStep(perItemAimKcal, safeDouble(nut.getKcal()));
                if (portion <= 0.5 && remainSlots > 1) continue;

                Nutrition snap = scaleNutrition(nut, portion);

                if (!passesItemRules(rules, cand, snap, mReq)) {
                    var step = stepDown(portion);
                    boolean fixed = false;
                    while (step.isPresent()) {
                        double p2 = step.getAsDouble();
                        Nutrition s2 = scaleNutrition(nut, p2);
                        if (passesItemRules(rules, cand, s2, mReq)) {
                            portion = p2; snap = s2; fixed = true; break;
                        }
                        step = stepDown(p2);
                    }
                    if (!fixed) continue;
                }

                // Lưu item
                mealPlanItemRepository.save(MealPlanItem.builder()
                        .day(day)
                        .mealSlot(slot)
                        .food(cand)
                        .portion(bd(portion,2))
                        .used(false)
                        .rank(rankBase++)
                        .nutrition(snap)
                        .build());

                usedThisSlot.add(cand.getId());
                recentFoods.add(cand.getId());

                // Trừ remaining theo vector
                remaining = subNutClamp0(remaining, snap);
                remainingKcal = safeDouble(remaining.getKcal());

                // Nếu remaining đã đạt (rất nhỏ) thì dừng slot
                if (isSatisfied(remaining)) break;

                picked++;
            }
        }
        // ===== 8) Trả về response =====
        mealPlanItemRepository.flush();

        entityManager.flush();
        entityManager.clear();

        MealPlanDay hydrated = mealPlanDayRepository.findByUser_IdAndDate(userId, date)
                .orElse(day);
        return mealPlanDayMapper.toMealPlanResponse(hydrated, cdnHelper);
    }

    /* ===================== HÀM PHỤ TRỢ ===================== */


    //Tính kcal mục tiêu / ngày
    public double calculateTargetKcal(double tdee, ProfileCreationRequest profile) {
        final double MAX_DAILY_ADJ   = 1000.0;    // ±1000 kcal/ngày
        final double MIN_KCAL_FEMALE = 1200.0;
        final double MIN_KCAL_MALE   = 1500.0;

        Integer deltaKg = profile.getTargetWeightDeltaKg();
        Integer weeks   = profile.getTargetDurationWeeks();

        double dailyAdj = 0.0;  // Số kcal cần tăng/giảm mỗi ngày
        boolean hasDelta = (deltaKg != null && deltaKg != 0) && (weeks != null && weeks > 0);
        if (hasDelta && profile.getGoal() != GoalType.MAINTAIN) {
            dailyAdj = (deltaKg * 7700.0) / (weeks * 7.0);
            dailyAdj = Math.max(-MAX_DAILY_ADJ, Math.min(MAX_DAILY_ADJ, dailyAdj));
        }

        // Nếu MAINTAIN, bỏ qua delta
        double targetCalories = (profile.getGoal() == GoalType.MAINTAIN) ? tdee : (tdee + dailyAdj);

        // Mức kcal tối thiểu theo giới tính
        targetCalories = switch (profile.getGender()){
            case FEMALE -> Math.max(MIN_KCAL_FEMALE, targetCalories);
            case MALE   -> Math.max(MIN_KCAL_MALE,   targetCalories);
            case OTHER  -> Math.max(MIN_KCAL_FEMALE, targetCalories);
        };
        return targetCalories;
    }

    //Tính nutrtion cho ngày
    public Nutrition caculateNutrition(MealPlanCreationRequest request,AggregateConstraints agg){
        final double FAT_PCT = 0.30;              // WHO: chat beo ≤30%
        final double FREE_SUGAR_PCT_MAX = 0.10;   // WHO: <10%
        final int    SODIUM_MG_LIMIT = 2000;      // WHO: <2000 mg natri/ngày

        var profile = request.getProfile();
        int weight = Math.max(1, profile.getWeightKg());

        //1) Tính TDEE
        double tdee = caculateBMI(profile);

        //2) Tính kcal mục tiêu / ngày
        double targetCalories = calculateTargetKcal(tdee, profile);

        //3.1) Protein (Đạm) theo g/kg
        double proteinPerKg = switch (profile.getGoal()) {
            case MAINTAIN -> 0.8;
            case LOSE     -> 1.0;
            case GAIN     -> 1.2;
        };
        double proteinG = weight * proteinPerKg;
        double proteinKcal = proteinG * 4.0;

        //3.2) Fat: 30% năng lượng (NẾU BÉO PHÌ NHỎ HƠN 30%)
        double fatKcal = targetCalories * FAT_PCT;
        double fatG = fatKcal / 9.0;

        //3.3) Carb = phần còn lại
        double carbKcal = Math.max(0.0, targetCalories - proteinKcal - fatKcal);
        double carbG = carbKcal / 4.0;

        //3.4) Fiber: tối thiểu 25g (nâng theo 14g/1000kcal nếu cần)
        double fiberG = Math.max(25.0, 14.0 * (targetCalories / 1000.0));

        //3.5) Free sugar trần <10% năng lượng → g → mg
        double sugarGMax = (targetCalories * FREE_SUGAR_PCT_MAX) / 4.0;
        double sugarMg = sugarGMax * 1000.0;

        // Target dinh dưỡng ngày
        Nutrition target = Nutrition.builder()
                .kcal(bd(targetCalories, 2))
                .proteinG(bd(proteinG, 2))
                .carbG(bd(carbG, 2))
                .fatG(bd(fatG, 2))
                .fiberG(bd(fiberG, 2))
                .sodiumMg(bd(SODIUM_MG_LIMIT, 2))
                .sugarMg(bd(sugarMg, 2))
                .build();

        return applyAggregateConstraintsToDayTarget(target, agg);
    }

    private static final double EPS_KCAL   = 40.0;
    private static final double EPS_PROT   = 3.0;
    private static final double EPS_CARB   = 6.0;
    private static final double EPS_FAT    = 3.0;
    private static final double EPS_FIBER  = 3.0;
    private static final double EPS_SODIUM = 200.0;
    private static final double EPS_SUGAR  = 6.0;

    private boolean isSatisfied(Nutrition n) {
        return  safeDouble(n.getKcal())     <= EPS_KCAL   &&
                safeDouble(n.getProteinG()) <= EPS_PROT   &&
                safeDouble(n.getCarbG())    <= EPS_CARB   &&
                safeDouble(n.getFatG())     <= EPS_FAT    &&
                safeDouble(n.getFiberG())   <= EPS_FIBER  &&
                safeDouble(n.getSodiumMg()) <= EPS_SODIUM &&
                safeDouble(n.getSugarMg())  <= EPS_SUGAR;
    }

    // remaining = max(0, target - consumed)
    private Nutrition subNutClamp0(Nutrition target, Nutrition consumed) {
        double kcal   = Math.max(0, safeDouble(target.getKcal())     - safeDouble(consumed.getKcal()));
        double prot   = Math.max(0, safeDouble(target.getProteinG()) - safeDouble(consumed.getProteinG()));
        double carb   = Math.max(0, safeDouble(target.getCarbG())    - safeDouble(consumed.getCarbG()));
        double fat    = Math.max(0, safeDouble(target.getFatG())     - safeDouble(consumed.getFatG()));
        double fiber  = Math.max(0, safeDouble(target.getFiberG())   - safeDouble(consumed.getFiberG()));
        double sodium = Math.max(0, safeDouble(target.getSodiumMg()) - safeDouble(consumed.getSodiumMg()));
        double sugar  = Math.max(0, safeDouble(target.getSugarMg())  - safeDouble(consumed.getSugarMg()));

        return Nutrition.builder()
                .kcal(bd(kcal,2)).proteinG(bd(prot,2)).carbG(bd(carb,2)).fatG(bd(fat,2))
                .fiberG(bd(fiber,2)).sodiumMg(bd(sodium,2)).sugarMg(bd(sugar,2))
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


    /* ===== TÍNH DINH DƯỠNG NGÀY ===== */
    private AggregateConstraints deriveAggregateConstraintsFromRules(List<NutritionRule> rules, int weightKg) {
        AggregateConstraints a = new AggregateConstraints();

        for (NutritionRule r : rules) {
            if (r.getScope() != RuleScope.DAY) continue;
            if (r.getTargetType() != TargetType.NUTRIENT) continue;
            if (r.getComparator() == null) continue;

            String code = safeStr(r.getTargetCode()).toUpperCase();
            BigDecimal min = r.getThresholdMin();
            BigDecimal max = r.getThresholdMax();

            // Quy đổi perKg → gram/day
            if (Boolean.TRUE.equals(r.getPerKg())) {
                if (min != null) min = min.multiply(BigDecimal.valueOf(weightKg));
                if (max != null) max = max.multiply(BigDecimal.valueOf(weightKg));
            }

            switch (code) {
                case "PROTEIN" -> applyBoundsToPair(a, r.getComparator(), min, max,
                        v -> a.dayProteinMin = maxOf(a.dayProteinMin, v),
                        v -> a.dayProteinMax = minOf(a.dayProteinMax, v));

                case "CARB" -> applyBoundsToPair(a, r.getComparator(), min, max,
                        v -> a.dayCarbMin = maxOf(a.dayCarbMin, v),
                        v -> a.dayCarbMax = minOf(a.dayCarbMax, v));

                case "FAT" -> applyBoundsToPair(a, r.getComparator(), min, max,
                        v -> a.dayFatMin = maxOf(a.dayFatMin, v),
                        v -> a.dayFatMax = minOf(a.dayFatMax, v));

                case "FIBER" -> applyBoundsToPair(a, r.getComparator(), min, max,
                        v -> a.dayFiberMin = maxOf(a.dayFiberMin, v),
                        v -> a.dayFiberMax = minOf(a.dayFiberMax, v));

                case "SODIUM" -> applyBoundsToPair(a, r.getComparator(), min, max,
                        v -> {},
                        v -> a.daySodiumMax = minOf(a.daySodiumMax, v));

                case "SUGAR" -> applyBoundsToPair(a, r.getComparator(), min, max,
                        v -> {},
                        v -> a.daySugarMax = minOf(a.daySugarMax, v));

                case "WATER" -> {
                    applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> a.dayWaterMin = maxOf(a.dayWaterMin, v),
                            v -> { /* thường không giới hạn trên với nước */ });
                }

                default -> { /* bỏ qua nutrient không hỗ trợ */ }
            }
        }
        return a;
    }

    private void applyBoundsToPair(
            AggregateConstraints a,
            com.hn.nutricarebe.enums.Comparator op,
            BigDecimal min,
            BigDecimal max,
            java.util.function.Consumer<BigDecimal> setMin,
            java.util.function.Consumer<BigDecimal> setMax
    ) {
        switch (op) {
            case LT, LTE -> { if (max != null) setMax.accept(max); }
            case GT, GTE -> { if (min != null) setMin.accept(min); }
            case EQ      -> {
                if (min != null) setMin.accept(min);
                if (min != null) setMax.accept(min);
            }
            case BETWEEN -> {
                if (min != null) setMin.accept(min);
                if (max != null) setMax.accept(max);
            }
            default -> {}
        }
    }

    private Nutrition applyAggregateConstraintsToDayTarget(Nutrition target, AggregateConstraints a) {
        BigDecimal protein = target.getProteinG();
        BigDecimal carb    = target.getCarbG();
        BigDecimal fat     = target.getFatG();
        BigDecimal fiber   = target.getFiberG();
        BigDecimal sodium  = target.getSodiumMg();
        BigDecimal sugar   = target.getSugarMg();


        // Protein (g/day)
        if (a.dayProteinMin != null && protein != null && protein.compareTo(a.dayProteinMin) < 0) protein = a.dayProteinMin;
        if (a.dayProteinMax != null && protein != null && protein.compareTo(a.dayProteinMax) > 0) protein = a.dayProteinMax;

        // Carb
        if (a.dayCarbMin != null && carb != null && carb.compareTo(a.dayCarbMin) < 0) carb = a.dayCarbMin;
        if (a.dayCarbMax != null && carb != null && carb.compareTo(a.dayCarbMax) > 0) carb = a.dayCarbMax;

        // Fat
        if (a.dayFatMin != null && fat != null && fat.compareTo(a.dayFatMin) < 0) fat = a.dayFatMin;
        if (a.dayFatMax != null && fat != null && fat.compareTo(a.dayFatMax) > 0) fat = a.dayFatMax;

        // Fiber
        if (a.dayFiberMin != null && fiber != null && fiber.compareTo(a.dayFiberMin) < 0) fiber = a.dayFiberMin;
        if (a.dayFiberMax != null && fiber != null && fiber.compareTo(a.dayFiberMax) > 0) fiber = a.dayFiberMax;

        // Sodium (mg/day)
        if (a.daySodiumMax != null && sodium != null && sodium.compareTo(a.daySodiumMax) > 0) sodium = a.daySodiumMax;

        // Sugar (mg/day)
        if (a.daySugarMax != null && sugar != null && sugar.compareTo(a.daySugarMax) > 0) sugar = a.daySugarMax;

        return Nutrition.builder()
                .kcal(target.getKcal())
                .proteinG(protein)
                .carbG(carb)
                .fatG(fat)
                .fiberG(fiber)
                .sodiumMg(sodium)
                .sugarMg(sugar)
                .build();
    }
    /* ===== TÍNH DINH DƯỠNG NGÀY ===== */


    /* ===== TÍNH DINH DƯỠNG BỮA ===== */
    private Nutrition approxMacroTargetForMeal(
            Nutrition dayTarget,
            double pctKcal,
            List<NutritionRule> rules,
            int weightKg,
            MealPlanCreationRequest request
    ) {
        double kcal  = safeDouble(dayTarget.getKcal()) * pctKcal;
        double ratio = kcal / Math.max(1, safeDouble(dayTarget.getKcal()));

        BigDecimal p = bd(safeDouble(dayTarget.getProteinG()) * ratio, 2);
        BigDecimal c = bd(safeDouble(dayTarget.getCarbG())    * ratio, 2);
        BigDecimal f = bd(safeDouble(dayTarget.getFatG())     * ratio, 2);
        BigDecimal fi= bd(Math.max(6.0, safeDouble(dayTarget.getFiberG()) * ratio), 2);
        BigDecimal na= bd(Math.min(700, 2000 * pctKcal), 2);
        BigDecimal su= bd(Math.max(0, safeDouble(dayTarget.getSugarMg()) * ratio), 2);

        Nutrition targetMeal = Nutrition.builder()
                .kcal(bd(kcal,2))
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
                    case "PROTEIN" -> applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> a.dayProteinMin = maxOf(a.dayProteinMin, v),
                            v -> a.dayProteinMax = minOf(a.dayProteinMax, v));

                    case "CARB" -> applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> a.dayCarbMin = maxOf(a.dayCarbMin, v),
                            v -> a.dayCarbMax = minOf(a.dayCarbMax, v));

                    case "FAT" -> applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> a.dayFatMin = maxOf(a.dayFatMin, v),
                            v -> a.dayFatMax = minOf(a.dayFatMax, v));

                    case "FIBER" -> applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> a.dayFiberMin = maxOf(a.dayFiberMin, v),
                            v -> a.dayFiberMax = minOf(a.dayFiberMax, v));

                    case "SODIUM" -> applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> {},
                            v -> a.daySodiumMax = minOf(a.daySodiumMax, v));

                    case "SUGAR" -> applyBoundsToPair(a, r.getComparator(), min, max,
                            v -> {},
                            v -> a.daySugarMax = minOf(a.daySugarMax, v));

                    case "WATER" -> {
                        applyBoundsToPair(a, r.getComparator(), min, max,
                                v -> a.dayWaterMin = maxOf(a.dayWaterMin, v),
                                v -> { /* thường không giới hạn trên với nước */ });
                    }

                    default -> { /* bỏ qua nutrient không hỗ trợ */ }
                }
            }
        }

        return applyAggregateConstraintsToDayTarget(targetMeal,a);
    }
    /* ===== TÍNH DINH DƯỠNG BỮA ===== */




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
        if (safeDouble(slotTarget.getSodiumMg()) > 0) sodiumPenalty = sodium / (safeDouble(slotTarget.getSodiumMg()) + 1e-6) * 2.0;

        double sugarPenalty = 0.0;
        if (slotTarget.getSugarMg() != null && slotTarget.getSugarMg().doubleValue() > 0) {
            sugarPenalty = sugarMg / (slotTarget.getSugarMg().doubleValue() + 1e-6) * 1.5;
        }

        double tagAdj = 0.0;
        if (f.getTags() != null) {
            if (f.getTags().contains(FoodTag.HIGH_FIBER)) tagAdj += 1.0;
            if (f.getTags().contains(FoodTag.LEAN_PROTEIN)) tagAdj += 1.0;
            if (f.getTags().contains(FoodTag.FRIED)) tagAdj -= 1.0;
            if (f.getTags().contains(FoodTag.SUGARY)) tagAdj -= 1.0;
            if (f.getTags().contains(FoodTag.PROCESSED)) tagAdj -= 0.6;
        }

        return -ratioPenalty + (kcalDensityScore / 300.0) + (fiberBonus * 0.2) + tagAdj - sodiumPenalty - sugarPenalty;
    }

    private double scoreFoodByLLMIfAny(Food food, MealSlot slot, Nutrition slotTarget) {
        // Placeholder để tích hợp LLM scorer nếu cần
        return 0.0;
    }
    /* ===== LỌC MÓN + TÍNH ĐIỂM ===== */
    //trả về giá trị nhỏ hơn (ưu tiên an toàn cho chất cần hạn chế)
    private BigDecimal minOf(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.min(b); }
    //trả về giá trị lớn hơn (ưu tiên đảm bảo cho chất cần thiết)
    private BigDecimal maxOf(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.max(b); }
}