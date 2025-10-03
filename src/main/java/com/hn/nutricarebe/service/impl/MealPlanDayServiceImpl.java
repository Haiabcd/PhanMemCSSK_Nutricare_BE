package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.mapper.MealPlanDayMapper;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.MealPlanDayService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;
import java.util.Comparator;
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

        //5.2) Fat: 30% năng lượng
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

        target = applyAggregateConstraintsToDayTarget(target, agg, weight);
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

            int minKcal = (int)Math.max(50, Math.round(perItem * 0.5));
            int maxKcal = (int)Math.round(perItem * 2.0);

            List<Food> pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                    slot.name(), minKcal, maxKcal, perItem, CANDIDATE_LIMIT
            );

            if (pool.size() < itemCount) {
                pool = foodRepository.selectCandidatesBySlotAndKcalWindow(
                        slot.name(), Math.max(30,(int)(perItem*0.3)), (int)(perItem*2.5), perItem, CANDIDATE_LIMIT
                );
            }

            // Tính target nutriton bữa
            Nutrition slotTarget = approxMacroTargetForSlot(target, slotKcalPct.get(slot));

            Map<UUID, Double> score = new HashMap<>();  //Mảng điểm của từng món
            for (Food f : pool) {
                // Tính điểm món bằng heuristic
                double s = scoreFoodHeuristic(f, slotTarget);
                // Cộng thêm điểm từ LLM (nếu có)
                s += scoreFoodByLLMIfAny(f, slot, slotTarget);
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
                Nutrition slotTarget = approxMacroTargetForSlot(dayTarget, slotKcalPct.get(slot));  //Tính dinh dưỡng mục tiêu bữa

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
                    // Nếu món không có đinh dưỡng loại này hoặc kcal ≤0 → bỏ qua
                    if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;

                    // Nếu món có tag trùng với tag đã dùng → 30% bỏ qua
                    if (!Collections.disjoint(usedTags, cand.getTags())) {
                        if (rng.nextDouble() < 0.30) continue;
                    }

                    // Dựa vào số kcal còn lại của bữa để tính khẩu phần món
                    double portion = clamp(kcalRemain / safeDouble(nut.getKcal()), 0.6, 1.6);
                    // Tính dinh dưỡng món theo khẩu phần
                    Nutrition snap = scaleNutrition(nut, portion);

                    // Kiểm tra dinh dưỡng món theo target từng món
                    boolean exceedNa = snap.getSodiumMg()!=null && slotTarget.getSodiumMg()!=null
                            && snap.getSodiumMg().compareTo(slotTarget.getSodiumMg()) > 0;
                    boolean exceedSu = snap.getSugarMg()!=null && slotTarget.getSugarMg()!=null
                            && snap.getSugarMg().compareTo(slotTarget.getSugarMg()) > 0;


                    if (exceedNa || exceedSu) {
                        // thử giảm 15% portion
                        double tryPortion = clamp(portion*0.85, 0.5, portion);
                        // tính lại dinh dưỡng
                        Nutrition trySnap = scaleNutrition(nut, tryPortion);
                        if (exceedNa && trySnap.getSodiumMg()!=null
                                     && slotTarget.getSodiumMg()!=null
                                     && trySnap.getSodiumMg().compareTo(slotTarget.getSodiumMg()) > 0)
                            continue; // vẫn vượt natri → bỏ món
                        if (exceedSu && trySnap.getSugarMg()!=null
                                     && slotTarget.getSugarMg()!=null
                                     && trySnap.getSugarMg().compareTo(slotTarget.getSugarMg()) > 0)
                            continue; // vẫn vượt đường → bỏ món
                        portion = tryPortion;
                        snap = trySnap;
                    }

                    if (!passesItemRules(rules, cand, snap, request)) {
                        // thử giảm 15% portion
                        double tryPortion2 = clamp(portion * 0.85, 0.5, portion);
                        // tính lại dinh dưỡng
                        Nutrition trySnap2 = scaleNutrition(nut, tryPortion2);

                        if (!passesItemRules(rules, cand, trySnap2, request)) {
                            continue; // bỏ món này
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

                    // cập nhật trạng thái no-repeat
                    Deque<UUID> dq = recentBySlot.get(slot);
                    dq.addLast(cand.getId());
                    while (dq.size() > noRepeatWindow * slotItemCounts.get(slot))
                        dq.removeFirst();

                    usedTags.addAll(cand.getTags());
                    kcalRemain = Math.max(0, kcalRemain - safeDouble(snap.getKcal()));
                    picked++;
                }

                // nếu vẫn thiếu món -> lấp món kcal thấp (CHƯA XEM)
                if (picked < itemCount) {
                    for (Food f : pool.stream().sorted(Comparator.comparingDouble(ff -> safeDouble(ff.getNutrition().getKcal()))).toList()) {
                        if (picked >= itemCount) break;
                        if (recentBySlot.get(slot).contains(f.getId())) continue;
                        var nut = f.getNutrition();
                        if (nut == null || nut.getKcal()==null || safeDouble(nut.getKcal())<=0) continue;
                        Nutrition snap = scaleNutrition(nut, 1.0);

                        // Kiểm tra rule ITEM khi lấp
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
        return mealPlanDayMapper.toMealPlanResponse(savedDays.getFirst());
    }

    /* ===================== HÀM PHỤ TRỢ ===================== */

    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }


    /* ===== CHẤM ĐIỂM TỪNG MÓN===== */
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
    /* ===== CHẤM ĐIỂM TỪNG MÓN===== */



    /* ===== TÍNH DINH DƯỠNG MÓN ===== */
    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
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

    /* ===== TÍNH DINH DƯỠNG BỮA ===== */
    private Nutrition approxMacroTargetForSlot(Nutrition dayTarget, double pctKcal) {
        // Tính calorie cho bữa ăn = lấy tổng calorie ngày *  với tỷ lệ phần trăm bữa.
        double kcal = safeDouble(dayTarget.getKcal()) * pctKcal;
        // Tính tỷ lệ phân bổ dinh dưỡng bằng cách chia calorie bữa cho calorie ngày.
        double ratio = kcal / Math.max(1, safeDouble(dayTarget.getKcal()));
        return Nutrition.builder()
                .kcal(bd(kcal, 2))
                .proteinG(bd(safeDouble(dayTarget.getProteinG()) * ratio, 2))
                .carbG(bd(safeDouble(dayTarget.getCarbG()) * ratio, 2))
                .fatG(bd(safeDouble(dayTarget.getFatG()) * ratio, 2))
                .fiberG(bd(Math.max(6.0, safeDouble(dayTarget.getFiberG()) * ratio), 2))
                .sodiumMg(bd(Math.min(700, 2000 * pctKcal), 2))
                .sugarMg(bd(Math.max(0, safeDouble(dayTarget.getSugarMg()) * ratio), 2))
                .build();
    }
    /* ===== TÍNH DINH DƯỠNG BỮA ===== */

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

            // Quy đổi perKg → gram/day cho PROTEIN (các chất khác thường không dùng perKg)
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


    private Nutrition applyAggregateConstraintsToDayTarget(Nutrition target, AggregateConstraints a, int weightKg) {
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
    /* ===== TÍNH DINH DƯỠNG NGÀY (XEM LẠI) ===== */





    // True nếu món + khẩu phần "snap" pass tất cả rule scope=ITEM
    private boolean passesItemRules(List<NutritionRule> rules, Food food, Nutrition snap, MealPlanCreationRequest request) {
        if (rules == null || rules.isEmpty()) return true;

        for (NutritionRule r : rules) {
            // Bỏ qua nếu rule không phải là từng món
            if (r.getScope() != RuleScope.ITEM) continue;
            if (!appliesToDemographics(r, request)) continue;

            switch (r.getTargetType()) {
                case FOOD_TAG -> {
                    Set<FoodTag> ruleTags = r.getFoodTags();
                    if (ruleTags == null || ruleTags.isEmpty()) break;

                    Set<FoodTag> foodTags = food.getTags();
                    boolean hasOverlap = foodTags != null && !Collections.disjoint(foodTags, ruleTags);
                    //Không có tag trùng --> false
                    // Có tag trùng --> true

                    //Nếu rule bắt món PHẢI CÓ các tags này, mà món KHÔNG CÓ → LOẠI BỎ
                    if (r.getComparator() == com.hn.nutricarebe.enums.Comparator.IN_SET && !hasOverlap) return false;
                    //Nếu rule bắt món KHÔNG ĐƯỢC CÓ các tags này, mà món CÓ → LOẠI BỎ
                    if (r.getComparator() == com.hn.nutricarebe.enums.Comparator.NOT_IN_SET && hasOverlap) return false;
                }
                case NUTRIENT -> {
                    String code = safeStr(r.getTargetCode());
                    BigDecimal value = getNutrientValueByCode(snap, code);
                    if (value == null) break;

                    BigDecimal min = r.getThresholdMin();
                    BigDecimal max = r.getThresholdMax();
                    if (Boolean.TRUE.equals(r.getPerKg())) {
                        int weight = Math.max(1, request.getProfile().getWeightKg());
                        if (min != null) min = min.multiply(BigDecimal.valueOf(weight));
                        if (max != null) max = max.multiply(BigDecimal.valueOf(weight));
                    }

                    if (!compare(value, min, max, r.getComparator()))
                        return false;
                }
            }
        }
        return true;
    }

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
    private boolean compare(BigDecimal value, BigDecimal min, BigDecimal max, com.hn.nutricarebe.enums.Comparator op) {
        if (value == null) return true;
        switch (op) {
            case LT:      return max != null && value.compareTo(max) < 0;
            case LTE:     return max != null && value.compareTo(max) <= 0;
            case GT:      return min != null && value.compareTo(min) > 0;
            case GTE:     return min != null && value.compareTo(min) >= 0;
            case EQ:      return (min != null) && value.compareTo(min) == 0;
            case BETWEEN: return (min != null && max != null) && value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
            default: return true;
        }
    }
    private BigDecimal minBD(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.min(b); }

    // Lấy giá trị dinh dưỡng theo mã code
    private BigDecimal getNutrientValueByCode(Nutrition n, String code) {
        if (n == null || code == null) return null;
        switch (code.toUpperCase()) {
            case "KCAL":
            case "ENERGY":      return n.getKcal();
            case "PROTEIN":     return n.getProteinG();
            case "CARB":
            case "CARBOHYDRATE":return n.getCarbG();
            case "FAT":         return n.getFatG();
            case "FIBER":       return n.getFiberG();
            case "NA":
            case "SODIUM":      return n.getSodiumMg();
            case "SUGAR":       return n.getSugarMg();
            default:            return null;
        }
    }

    //kiểm tra so sánh "nhỏ hơn" (≤ hoặc <) để áp dụng ngưỡng lớn nhất.
    private boolean isLTE(com.hn.nutricarebe.enums.Comparator c) {
        return c == com.hn.nutricarebe.enums.Comparator.LTE || c == com.hn.nutricarebe.enums.Comparator.LT;
    }
    //kiểm tra so sánh "lớn hơn" (≥ hoặc > hoặc =) để áp dụng ngưỡng nhỏ nhất.
    private boolean isGTE(com.hn.nutricarebe.enums.Comparator c) {
        return c == com.hn.nutricarebe.enums.Comparator.GTE || c == com.hn.nutricarebe.enums.Comparator.GT || c == com.hn.nutricarebe.enums.Comparator.EQ;
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
