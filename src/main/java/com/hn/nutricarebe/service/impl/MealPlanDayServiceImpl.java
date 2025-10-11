package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.MealPlanDayMapper;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.MealPlanDayService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.Comparator;
import java.util.stream.Collectors;


import static java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanDayServiceImpl implements MealPlanDayService {

    MealPlanDayRepository mealPlanDayRepository;
    MealPlanDayMapper mealPlanDayMapper;
    FoodRepository foodRepository;
    MealPlanItemRepository mealPlanItemRepository;
    // Nạp bệnh nền, dị ứng, rules
    UserConditionRepository userConditionRepository;
    UserAllergyRepository userAllergyRepository;
    NutritionRuleRepository nutritionRuleRepository;
    CdnHelper cdnHelper;




    //Tính BMI
    public double caculateBMI(ProfileCreationRequest profile) {
        int currentYear = Year.now().getValue();
        int age    = Math.max(0, currentYear - profile.getBirthYear());
        int weight = Math.max(1, profile.getWeightKg());
        int height = Math.max(50, profile.getHeightCm());

        // 1) BMR: Mifflin–St Jeor
        double bmr = switch (profile.getGender()) {
            case MALE   -> 10 * weight + 6.25 * height - 5 * age + 5;
            case FEMALE -> 10 * weight + 6.25 * height - 5 * age - 161;
            case OTHER  -> 10 * weight + 6.25 * height - 5 * age;
        };

        // 2) TDEE theo mức độ hoạt động
        ActivityLevel al = profile.getActivityLevel() != null ? profile.getActivityLevel() : ActivityLevel.SEDENTARY;
        double activityFactor = switch (al) {
            case SEDENTARY         -> 1.2;
            case LIGHTLY_ACTIVE    -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE       -> 1.725;
            case EXTRA_ACTIVE      -> 1.9;
        };
        return bmr * activityFactor;
    }

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


    @Override
    public MealPlanResponse createPlan(MealPlanCreationRequest request, int number) {
        final double FAT_PCT = 0.30;              // WHO: chat beo ≤30%
        final double FREE_SUGAR_PCT_MAX = 0.10;   // WHO: <10%
        final int    SODIUM_MG_LIMIT = 2000;      // WHO: <2000 mg natri/ngày
        final double WATER_ML_PER_KG = 35.0;      // 30–35 ml/kg

        var profile = request.getProfile();
        int weight = Math.max(1, profile.getWeightKg());

        //1) Tính TDEE
        double tdee = caculateBMI(profile);

        //2) Tính kcal mục tiêu / ngày
        double targetCalories = calculateTargetKcal(tdee, profile);

        //3) Nước
        double waterMl = weight * WATER_ML_PER_KG;

        //5.1) Protein (Đạm) theo g/kg
        double proteinPerKg = switch (profile.getGoal()) {
            case MAINTAIN -> 0.8;
            case LOSE     -> 1.0;
            case GAIN     -> 1.2;
        };
        double proteinG = weight * proteinPerKg;
        double proteinKcal = proteinG * 4.0;

        //5.2) Fat: 30% năng lượng (NẾU BÉO PHÌ NHỎ HƠN 30%)
        double fatKcal = targetCalories * FAT_PCT;
        double fatG = fatKcal / 9.0;

        //5.3) Carb = phần còn lại
        double carbKcal = Math.max(0.0, targetCalories - proteinKcal - fatKcal);
        double carbG = carbKcal / 4.0;

        //5.4) Fiber: tối thiểu 25g (nâng theo 14g/1000kcal nếu cần)
        double fiberG = Math.max(25.0, 14.0 * (targetCalories / 1000.0));

        //5.5) Free sugar trần <10% năng lượng → g → mg
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

        /* ===================== NẠP BỆNH NỀN, DỊ ỨNG & RULES ===================== */
        UUID userId = request.getUserId();

        Set<UUID> conditionIds = new HashSet<>();
        userConditionRepository.findByUser_Id(userId)
                .forEach(uc -> conditionIds.add(uc.getCondition().getId()));

        Set<UUID> allergyIds = new HashSet<>();
        userAllergyRepository.findByUser_Id(userId)
                .forEach(ua -> allergyIds.add(ua.getAllergy().getId()));

        //Lấy ra danh sách rule của bệnh nền & dị ứng mà người dùng bị
        List<NutritionRule> rules = nutritionRuleRepository.findActiveByConditionsOrAllergies(
                conditionIds, allergyIds, conditionIds.isEmpty(), allergyIds.isEmpty()
        );

        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);

        if (agg.dayWaterMin != null) {
            waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        }

        target = applyAggregateConstraintsToDayTarget(target, agg);
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
                    .filter(f -> Collections.disjoint(tagsOf(f), tagDir.avoid))
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
                if (!tagDir.preferBonus.isEmpty()) {
                    long cnt = ft.stream().filter(tagDir.preferBonus::containsKey).count();  //Đếm số tag trùng
                    if (cnt > 0) s += cnt * PREFER_BONUS_PER_TAG;
                }
                // LIMIT: trừ mềm theo số tag khớp
                if (!tagDir.limitPenalty.isEmpty()) {
                    long cnt = ft.stream().filter(tagDir.limitPenalty::containsKey).count();
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
    public MealPlanResponse getMealPlanByDate(LocalDate date) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        MealPlanDay m = mealPlanDayRepository.findByUser_IdAndDate(userId, date)
                .orElseThrow(() -> new AppException(ErrorCode.MEAL_PLAN_NOT_FOUND));
        return mealPlanDayMapper.toMealPlanResponse(m, cdnHelper);
    }
    /* ===================== HÀM PHỤ TRỢ ===================== */

    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
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
                if (!appliesToDemographics(r, request)) continue;

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


    /* ===== TÍNH DINH DƯỠNG MÓN ===== */
    private Nutrition scaleNutrition(Nutrition base, double portion) {
        return Nutrition.builder()
                .kcal(bd(safeDouble(base.getKcal()) * portion, 2))
                .proteinG(bd(safeDouble(base.getProteinG()) * portion, 2))
                .carbG(bd(safeDouble(base.getCarbG()) * portion, 2))
                .fatG(bd(safeDouble(base.getFatG()) * portion, 2))
                .fiberG(bd(safeDouble(base.getFiberG()) * portion, 2))
                .sodiumMg(bd(safeDouble(base.getSodiumMg()) * portion, 2))
                .sugarMg(bd(safeDouble(base.getSugarMg()) * portion, 2))
                .build();
    }
    /* ===== TÍNH DINH DƯỠNG MÓN ===== */

    /* ===== LỌC MÓN + TÍNH ĐIỂM ===== */
    static class TagDirectives {
        Set<FoodTag> avoid = new HashSet<>();
        Map<FoodTag, Double> preferBonus = new HashMap<>();
        Map<FoodTag, Double> limitPenalty = new HashMap<>();
    }

    private TagDirectives buildTagDirectives(List<NutritionRule> rules, MealPlanCreationRequest request) {
        TagDirectives d = new TagDirectives();
        if (rules == null || rules.isEmpty()) return d;

        for (NutritionRule r : rules) {
            if (r.getTargetType() != TargetType.FOOD_TAG) continue;
            if (r.getScope() != RuleScope.ITEM) continue;
            if (!appliesToDemographics(r, request)) continue;

            Set<FoodTag> tags = r.getFoodTags();
            if (tags == null || tags.isEmpty()) continue;

            RuleType rt = r.getRuleType();
            if (rt == null) continue;

            switch (rt) {
                case AVOID -> d.avoid.addAll(tags);
                case PREFER -> tags.forEach(t -> d.preferBonus.merge(t, 1.0, Double::sum));
                case LIMIT  -> tags.forEach(t -> d.limitPenalty.merge(t, 1.0, Double::sum));
                default -> { /* bỏ qua các loại khác nếu có */ }
            }
        }
        return d;
    }

    private static Set<FoodTag> tagsOf(Food f) {
        return f.getTags() == null ? Collections.emptySet() : f.getTags();
    }

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

    /* ===== CHỌN KHẨU PHẦN ===== */
    static final double[] PORTION_STEPS = {1.5, 1.0, 0.5};
    private static double pickPortionStep(double kcalRemain, double foodKcal) {
        if (foodKcal <= 0) return 1.0;
        double best = PORTION_STEPS[0];
        double bestDiff = Math.abs(kcalRemain - best * foodKcal);  //Độ lệch ||

        for (double step : PORTION_STEPS) {
            double diff = Math.abs(kcalRemain - step * foodKcal);  //Độ lệch từng bậc ||

            boolean overBest = best * foodKcal > kcalRemain;   //Kiểm tra bậc hiện tại  (true --> vượt)
            boolean overCur  = step * foodKcal > kcalRemain;   //Kiểm tra bậc đang xét

            if (diff < bestDiff || (Math.abs(diff - bestDiff) < 1e-6 && !overCur && overBest)) {
                best = step;
                bestDiff = diff;
            }
        }
        return best;
    }
    private static OptionalDouble stepDown(double current) {
        for (int i = 0; i < PORTION_STEPS.length; i++) {
            if (Math.abs(PORTION_STEPS[i] - current) < 1e-9) {
                // Tìm bậc kế tiếp nhỏ hơn
                for (int j = i + 1; j < PORTION_STEPS.length; j++) {
                    if (PORTION_STEPS[j] < PORTION_STEPS[i]) {
                        return OptionalDouble.of(PORTION_STEPS[j]);
                    }
                }
                break;
            }
        }
        return OptionalDouble.empty();
    }
    /* ===== CHỌN KHẨU PHẦN ===== */

    /* ===== KIỂM TRA LẠI MÓN ĐƯỢC CHỌN ===== */
    private boolean passesItemRules(List<NutritionRule> rules, Food food, Nutrition snap, MealPlanCreationRequest request) {
        if (rules == null || rules.isEmpty()) return true;
        for (NutritionRule r : rules) {
            // Bỏ qua nếu rule không phải là từng món
            if (r.getScope() != RuleScope.ITEM) continue;
            if (!appliesToDemographics(r, request)) continue;

            if (r.getTargetType() == TargetType.NUTRIENT) {
                String code = safeStr(r.getTargetCode());
                //Lấy chất dinh dưỡng theo mã code
                BigDecimal value = switch (code){
                        case "KCAL" -> snap.getKcal();
                        case "PROTEIN"        -> snap.getProteinG();
                        case "CARB"           -> snap.getCarbG();
                        case "FAT"            -> snap.getFatG();
                        case "FIBER"          -> snap.getFiberG();
                        case "SODIUM"         -> snap.getSodiumMg();
                        case "SUGAR"          -> snap.getSugarMg();
                        default               -> null;
                };
                if (value == null) break;

                BigDecimal min = r.getThresholdMin();
                BigDecimal max = r.getThresholdMax();

                if (Boolean.TRUE.equals(r.getPerKg())) {
                    int weight = Math.max(1, request.getProfile().getWeightKg());
                    if (min != null) min = min.multiply(BigDecimal.valueOf(weight));
                    if (max != null) max = max.multiply(BigDecimal.valueOf(weight));
                }

                switch (r.getComparator()) {
                    case LT:      return max != null && value.compareTo(max) < 0;
                    case LTE:     return max != null && value.compareTo(max) <= 0;
                    case GT:      return min != null && value.compareTo(min) > 0;
                    case GTE:     return min != null && value.compareTo(min) >= 0;
                    case EQ:      return (min != null) && value.compareTo(min) == 0;
                    case BETWEEN: return (min != null && max != null) && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
                    default: return true;
                }
            }
        }
        return true;
    }
    /* ===== KIỂM TRA LẠI MÓN ĐƯỢC CHỌN ===== */

    // Kiểm tra rule về độ tuổi, giới tính
    private boolean appliesToDemographics(NutritionRule r, MealPlanCreationRequest request) {
        var p = request.getProfile();
        // Giới tính
        Gender ruleSex = r.getApplicableSex();
        if (ruleSex != null) {
            if (p.getGender() == null || p.getGender() != ruleSex) return false;
        }
        // Độ tuổi
        int currentYear = java.time.Year.now().getValue();
        int age = Math.max(0, currentYear - p.getBirthYear());
        if (r.getAgeMin() != null && age < r.getAgeMin()) return false;
        if (r.getAgeMax() != null && age > r.getAgeMax()) return false;
        return true;
    }


    //trả về giá trị nhỏ hơn (ưu tiên an toàn cho chất cần hạn chế)
    private BigDecimal minOf(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.min(b); }
    //trả về giá trị lớn hơn (ưu tiên đảm bảo cho chất cần thiết)
    private BigDecimal maxOf(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.max(b); }

    private double safeDouble(BigDecimal x){
        return x == null ? 0.0 : x.doubleValue();
    }
    private String safeStr(String s){ return s==null? "" : s.trim(); }
}
