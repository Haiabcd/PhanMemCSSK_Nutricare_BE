package com.hn.nutricarebe.helper;


import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;


public final class MealPlanHelper {
    private MealPlanHelper() {}

    public static final double[] PORTION_STEPS = {1.5, 1.0, 0.5};

    public static final Map<MealSlot, Double> SLOT_KCAL_PCT =
            Collections.unmodifiableMap(new EnumMap<>(Map.of(
                    MealSlot.BREAKFAST, 0.25,
                    MealSlot.LUNCH,     0.30,
                    MealSlot.DINNER,    0.30,
                    MealSlot.SNACK,     0.15
            )));

    public static final Map<MealSlot, Integer> SLOT_ITEM_COUNTS =
            Collections.unmodifiableMap(new EnumMap<>(Map.of(
                    MealSlot.BREAKFAST, 2,
                    MealSlot.LUNCH,     3,
                    MealSlot.DINNER,    3,
                    MealSlot.SNACK,     1
            )));


    public static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
    public static double safeDouble(BigDecimal x){
        return x == null ? 0.0 : x.doubleValue();
    }

    public static String safeStr(String s){ return s==null? "" : s.trim(); }

    //trả về giá trị nhỏ hơn (ưu tiên an toàn cho chất cần hạn chế)
    public static BigDecimal minOf(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.min(b); }
    //trả về giá trị lớn hơn (ưu tiên đảm bảo cho chất cần thiết)
    public static BigDecimal maxOf(BigDecimal a, BigDecimal b){ if (a==null) return b; if (b==null) return a; return a.max(b); }

    //Tính TDEE
    public static double caculateTDEE(ProfileCreationRequest profile) {
        int currentYear = Year.now().getValue();
        int age    = Math.max(0, currentYear - profile.getBirthYear());
        int weight = Math.max(1, profile.getWeightKg());
        int height = Math.max(50, profile.getHeightCm());

        // 1) BMR: Mifflin–St Jeor
        double base = 10 * weight + 6.25 * height - 5 * age;
        double sexAdj = switch (profile.getGender()) {
            case MALE   -> 5;
            case FEMALE -> -161;
            case OTHER  -> 0;
        };
        double bmr = base + sexAdj;

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

    //Cộng 2 chất dinh dưỡng
    public static Nutrition addNut(Nutrition a, Nutrition b) {
        if (a == null) return b;
        if (b == null) return a;
        return Nutrition.builder()
                .kcal(     bd(safeDouble(a.getKcal())     + safeDouble(b.getKcal()), 2))
                .proteinG( bd(safeDouble(a.getProteinG()) + safeDouble(b.getProteinG()), 2))
                .carbG(    bd(safeDouble(a.getCarbG())    + safeDouble(b.getCarbG()), 2))
                .fatG(     bd(safeDouble(a.getFatG())     + safeDouble(b.getFatG()), 2))
                .fiberG(   bd(safeDouble(a.getFiberG())   + safeDouble(b.getFiberG()), 2))
                .sodiumMg( bd(safeDouble(a.getSodiumMg()) + safeDouble(b.getSodiumMg()), 2))
                .sugarMg(  bd(safeDouble(a.getSugarMg())  + safeDouble(b.getSugarMg()), 2))
                .build();
    }

    // Kiểm tra rule về độ tuổi, giới tính
    public static boolean isApplicableToDemographics(NutritionRule r, MealPlanCreationRequest request) {
        var p = request.getProfile();
        Gender ruleSex = r.getApplicableSex();
        boolean genderOk = (ruleSex == null) || (p.getGender() != null && p.getGender() == ruleSex);
        int currentYear = java.time.Year.now().getValue();
        int age = Math.max(0, currentYear - p.getBirthYear());
        Integer min = r.getAgeMin();
        Integer max = r.getAgeMax();
        boolean ageOk = (min == null || age >= min) && (max == null || age <= max);
        return genderOk && ageOk;
    }


    // Lấy tập tag của món, tránh null
    public static Set<String> tagsOf(Food f) {
        return (f == null || f.getTags() == null)
                ? Collections.emptySet()
                : f.getTags().stream()
                .map(Tag::getNameCode)
                .filter(Objects::nonNull)  // Lọc bỏ các giá trị null trong Stream
                .collect(Collectors.toSet());
    }
    // Lấy tập tag của nutrition, tránh null
    public static Set<String> tagsOfNu(Set<Tag> tags) {
        return (tags == null || tags.isEmpty())
                ? Collections.emptySet()
                : tags.stream()
                .map(Tag::getNameCode)
                .filter(Objects::nonNull)  // Lọc bỏ các giá trị null trong Stream
                .collect(Collectors.toSet());
    }



    /* ===== TÍNH DINH DƯỠNG MÓN ===== */
    public static Nutrition scaleNutrition(Nutrition base, double portion) {
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

    //Tìm các tag cần tránh, ưu tiên, giới hạn từ rule
    public static TagDirectives buildTagDirectives(List<NutritionRule> rules, MealPlanCreationRequest request) {
        TagDirectives d = new TagDirectives();
        if (rules == null || rules.isEmpty()) return d;

        for (NutritionRule r : rules) {
            if (r.getTargetType() != TargetType.FOOD_TAG) continue;
            if (r.getScope() != RuleScope.ITEM) continue;
            if (!isApplicableToDemographics(r, request)) continue;

            Set<String> tags = tagsOfNu(r.getTags());

            RuleType rt = r.getRuleType();
            if (rt == null) continue;

            switch (rt) {
                case AVOID -> d.getAvoid().addAll(tags);
                case PREFER -> tags.forEach(t -> d.getPreferBonus().merge(t, 1.0, Double::sum));
                case LIMIT  -> tags.forEach(t -> d.getLimitPenalty().merge(t, 1.0, Double::sum));
                default -> { /* bỏ qua các loại khác nếu có */ }
            }
        }
        return d;
    }

    /* ===== KIỂM TRA LẠI MÓN ĐƯỢC CHỌN ===== */
    private static BigDecimal nutrientValueOf(String code, Nutrition n) {
        if (n == null) return null;
        return switch (safeStr(code).toUpperCase()) {
            case "KCAL"    -> n.getKcal();
            case "PROTEIN" -> n.getProteinG();
            case "CARB"    -> n.getCarbG();
            case "FAT"     -> n.getFatG();
            case "FIBER"   -> n.getFiberG();
            case "SODIUM"  -> n.getSodiumMg();
            case "SUGAR"   -> n.getSugarMg();
            default        -> null;
        };
    }
    public static boolean passesItemRules(List<NutritionRule> rules, Nutrition snap, MealPlanCreationRequest request) {
        if (rules == null || rules.isEmpty()) return true;
        for (NutritionRule r : rules) {
            // Bỏ qua nếu rule không phải là từng món
            if (r.getScope() != RuleScope.ITEM) continue;
            if (!isApplicableToDemographics(r, request)) continue;

            if (r.getTargetType() == TargetType.NUTRIENT) {
                BigDecimal value = nutrientValueOf(r.getTargetCode(), snap);
                if (value == null) break;

                BigDecimal min = r.getThresholdMin();
                BigDecimal max = r.getThresholdMax();

                if (Boolean.TRUE.equals(r.getPerKg())) {
                    int weight = Math.max(1, request.getProfile().getWeightKg());
                    if (min != null) min = min.multiply(BigDecimal.valueOf(weight));
                    if (max != null) max = max.multiply(BigDecimal.valueOf(weight));
                }

                return switch (r.getComparator()) {
                    case LT      -> (max != null) && value.compareTo(max) < 0;
                    case LTE     -> (max != null) && value.compareTo(max) <= 0;
                    case GT      -> (min != null) && value.compareTo(min) > 0;
                    case GTE     -> (min != null) && value.compareTo(min) >= 0;
                    case EQ      -> (min != null) && value.compareTo(min) == 0;
                    case BETWEEN -> (min != null && max != null)
                            && value.compareTo(min) >= 0
                            && value.compareTo(max) <= 0;
                    default      -> true;
                };
            }
        }
        return true;
    }
    /* ===== KIỂM TRA LẠI MÓN ĐƯỢC CHỌN ===== */

    /* ===== CHỌN KHẨU PHẦN ===== */
    public static double pickPortionStep(double kcalRemain, double foodKcal) {
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
    public static OptionalDouble stepDown(double current) {
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


    /* ===== TÍNH DINH DƯỠNG NGÀY ===== */
    public static  AggregateConstraints deriveAggregateConstraintsFromRules(List<NutritionRule> rules, int weightKg) {
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
                case "PROTEIN" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> a.dayProteinMin = maxOf(a.dayProteinMin, v),
                        v -> a.dayProteinMax = minOf(a.dayProteinMax, v));

                case "CARB" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> a.dayCarbMin = maxOf(a.dayCarbMin, v),
                        v -> a.dayCarbMax = minOf(a.dayCarbMax, v));

                case "FAT" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> a.dayFatMin = maxOf(a.dayFatMin, v),
                        v -> a.dayFatMax = minOf(a.dayFatMax, v));

                case "FIBER" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> a.dayFiberMin = maxOf(a.dayFiberMin, v),
                        v -> a.dayFiberMax = minOf(a.dayFiberMax, v));

                case "SODIUM" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> {},
                        v -> a.daySodiumMax = minOf(a.daySodiumMax, v));

                case "SUGAR" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> {},
                        v -> a.daySugarMax = minOf(a.daySugarMax, v));

                case "WATER" -> applyBoundsToPair( r.getComparator(), min, max,
                        v -> a.dayWaterMin = maxOf(a.dayWaterMin, v),
                        v -> { /* thường không giới hạn trên với nước */ });
                default -> { /* bỏ qua nutrient không hỗ trợ */ }
            }
        }
        return a;
    }

    public static void applyBoundsToPair(
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

    //Tính kcal mục tiêu / ngày
    public static double calculateTargetKcal(double tdee, ProfileCreationRequest profile) {
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
        targetCalories = (profile.getGender() == Gender.MALE)
                ? Math.max(MIN_KCAL_MALE, targetCalories)
                : Math.max(MIN_KCAL_FEMALE, targetCalories);
        return targetCalories;
    }

    //Tính nutrtion cho ngày
    public static Nutrition caculateNutrition(ProfileCreationRequest profile,AggregateConstraints agg){
        final double FAT_PCT = 0.30;              // WHO: chat beo ≤30%
        final double FREE_SUGAR_PCT_MAX = 0.10;   // WHO: <10%
        final int    SODIUM_MG_LIMIT = 2000;      // WHO: <2000 mg natri/ngày

        int weight = Math.max(1, profile.getWeightKg());

        //1) Tính TDEE
        double tdee = caculateTDEE(profile);

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

    public static Nutrition applyAggregateConstraintsToDayTarget(Nutrition target, AggregateConstraints a) {
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
}
