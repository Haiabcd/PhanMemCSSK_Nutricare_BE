package com.hn.nutricarebe.helper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.*;
import java.util.stream.Collectors;
import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.*;


public final class MealPlanHelper {
    private MealPlanHelper() {}

    public static final double[] PORTION_STEPS = {1.5, 1.0, 0.5};

    public static final Map<MealSlot, Double> SLOT_KCAL_PCT = Collections.unmodifiableMap(new EnumMap<>(Map.of(
            MealSlot.BREAKFAST, 0.25,
            MealSlot.LUNCH, 0.30,
            MealSlot.DINNER, 0.30,
            MealSlot.SNACK, 0.15)));

    public static final Map<MealSlot, Integer> SLOT_ITEM_COUNTS = Collections.unmodifiableMap(new EnumMap<>(Map.of(
            MealSlot.BREAKFAST, 2,
            MealSlot.LUNCH, 3,
            MealSlot.DINNER, 3,
            MealSlot.SNACK, 1)));

    // Biên độ chấp nhận tính theo tỷ lệ actual/target
    public static final double KCAL_MIN_RATIO   = 0.95;
    public static final double KCAL_MAX_RATIO   = 1.05;
    public static final double CARB_MIN_RATIO   = 0.85;
    public static final double CARB_MAX_RATIO   = 1.15;
    public static final double FIBER_MIN_RATIO  = 0.95;
    public static final double FIBER_MAX_RATIO  = 1.50;
    public static final double PROT_MIN_RATIO   = 0.90;
    public static final double PROT_MAX_RATIO   = 1.10;
    public static final double FAT_MIN_RATIO    = 0.80;
    public static final double FAT_MAX_RATIO    = 1.10;

    public static final double EPS_KCAL = 30.0;
    public static final double EPS_PROT = 3.0;
    public static final double EPS_CARB = 6.0;
    public static final double EPS_FAT = 3.0;
    public static final double EPS_FIBER = 2.0;

    public static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }

    public static double safeDouble(BigDecimal x) {
        return x == null ? 0.0 : x.doubleValue();
    }

    public static String safeStr(String s) {
        return s == null ? "" : s.trim();
    }

    // trả về giá trị nhỏ hơn (ưu tiên an toàn cho chất cần hạn chế)
    public static BigDecimal minOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.min(b);
    }
    // trả về giá trị lớn hơn (ưu tiên đảm bảo cho chất cần thiết)
    public static BigDecimal maxOf(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.max(b);
    }

    // Tính TDEE theo Mifflin-St Jeor
    public static double caculateTDEE(ProfileCreationRequest profile) {
        int currentYear = Year.now().getValue();
        int age = currentYear - profile.getBirthYear();
        int weight = profile.getWeightKg();
        int height = profile.getHeightCm();
        // 1) BMR
        double base = 10 * weight + 6.25 * height - 5 * age;
        double sexAdj =
                switch (profile.getGender()) {
                    case MALE -> 5;
                    case FEMALE -> -161;
                    case OTHER -> 0;
                };
        double bmr = base + sexAdj;
        // 2) TDEE
        ActivityLevel al = profile.getActivityLevel() != null ? profile.getActivityLevel() : ActivityLevel.SEDENTARY;
        double activityFactor =
                switch (al) {
                    case SEDENTARY -> 1.2;
                    case LIGHTLY_ACTIVE -> 1.375;
                    case MODERATELY_ACTIVE -> 1.55;
                    case VERY_ACTIVE -> 1.725;
                    case EXTRA_ACTIVE -> 1.9;
                };
        return bmr * activityFactor;
    }

    // Cộng 2 chất dinh dưỡng
    public static Nutrition addNut(Nutrition a, Nutrition b) {
        if (a == null) return b;
        if (b == null) return a;
        return Nutrition.builder()
                .kcal(bd(safeDouble(a.getKcal()) + safeDouble(b.getKcal()), 2))
                .proteinG(bd(safeDouble(a.getProteinG()) + safeDouble(b.getProteinG()), 2))
                .carbG(bd(safeDouble(a.getCarbG()) + safeDouble(b.getCarbG()), 2))
                .fatG(bd(safeDouble(a.getFatG()) + safeDouble(b.getFatG()), 2))
                .fiberG(bd(safeDouble(a.getFiberG()) + safeDouble(b.getFiberG()), 2))
                .sodiumMg(bd(safeDouble(a.getSodiumMg()) + safeDouble(b.getSodiumMg()), 2))
                .sugarMg(bd(safeDouble(a.getSugarMg()) + safeDouble(b.getSugarMg()), 2))
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

    // Lấy tập tag của món (f), tránh null
    public static Set<String> tagsOf(Food f) {
        return (f == null || f.getTags() == null)
                ? Collections.emptySet()
                : f.getTags().stream()
                        .map(Tag::getNameCode)
                        .filter(Objects::nonNull) // Lọc bỏ các giá trị null trong Stream
                        .collect(Collectors.toSet());
    }

    // Lấy tập tag của nutrition, tránh null
    public static Set<String> tagsOfNu(Set<Tag> tags) {
        return (tags == null || tags.isEmpty())
                ? Collections.emptySet()
                : tags.stream()
                        .map(Tag::getNameCode)
                        .filter(Objects::nonNull)
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

    // Tổng hợp tag cần: tránh, ưu tiên, giới hạn (món)
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
                case LIMIT -> tags.forEach(t -> d.getLimitPenalty().merge(t, 1.0, Double::sum));
                default -> {
                    /* bỏ qua các loại khác nếu có */
                }
            }
        }
        return d;
    }

    /* ===== KIỂM TRA LẠI MÓN ĐƯỢC CHỌN ===== */
    private static BigDecimal nutrientValueOf(String code, Nutrition n) {
        if (n == null) return null;
        return switch (safeStr(code).toUpperCase()) {
            case "KCAL" -> n.getKcal();
            case "PROTEIN" -> n.getProteinG();
            case "CARB" -> n.getCarbG();
            case "FAT" -> n.getFatG();
            case "FIBER" -> n.getFiberG();
            case "SODIUM" -> n.getSodiumMg();
            case "SUGAR" -> n.getSugarMg();
            default -> null;
        };
    }

    public static boolean passesItemRules(List<NutritionRule> rules, Nutrition snap, MealPlanCreationRequest request) {
        if (rules == null || rules.isEmpty()) return true;
        for (NutritionRule r : rules) {
            if (r.getScope() != RuleScope.ITEM) continue;
            if (!isApplicableToDemographics(r, request)) continue;
            if (r.getTargetType() == TargetType.NUTRIENT) {
                BigDecimal value = nutrientValueOf(r.getTargetCode(), snap);
                if (value == null) continue;
                BigDecimal min = r.getThresholdMin();
                BigDecimal max = r.getThresholdMax();
                if (Boolean.TRUE.equals(r.getPerKg())) {
                    int weight = Math.max(1, request.getProfile().getWeightKg());
                    if (min != null) min = min.multiply(BigDecimal.valueOf(weight));
                    if (max != null) max = max.multiply(BigDecimal.valueOf(weight));
                }
                boolean ok = switch (r.getComparator()) {
                    case LT   -> (max != null) && value.compareTo(max) < 0;
                    case LTE  -> (max != null) && value.compareTo(max) <= 0;
                    case GT   -> (min != null) && value.compareTo(min) > 0;
                    case GTE  -> (min != null) && value.compareTo(min) >= 0;
                    case EQ   -> (min != null) && value.compareTo(min) == 0;
                    case BETWEEN -> (min != null && max != null)
                            && value.compareTo(min) >= 0
                            && value.compareTo(max) <= 0;
                };
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }

    /* ===== KIỂM TRA LẠI MÓN ĐƯỢC CHỌN ===== */

    /* ===== CHỌN KHẨU PHẦN ===== */
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

    /* ========================================== DINH DƯỠNG NGÀY ================================================ */

    //Tính mix/max dinh dưỡng (ngày) theo rule
    public static AggregateConstraints deriveAggregateConstraintsFromRules(List<NutritionRule> rules, int weightKg) {
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
                            /* không giới hạn trên với nước */
                        });
                default -> {
                    /* bỏ qua nutrient không hỗ trợ */
                }
            }
        }
        return a;
    }

    // Áp dụng bounds từ comparator vào cặp min/max
    public static void applyBoundsToPair(
            com.hn.nutricarebe.enums.Comparator op,
            BigDecimal min,
            BigDecimal max,
            java.util.function.Consumer<BigDecimal> setMin,
            java.util.function.Consumer<BigDecimal> setMax) {
        switch (op) {
            case LT, LTE -> {
                if (max != null) setMax.accept(max);
            }
            case GT, GTE -> {
                if (min != null) setMin.accept(min);
            }
            case EQ -> {
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

    /* ========================================== DINH DƯỠNG NGÀY ================================================ */

    /* ========================================== DINH DƯỠNG BỮA ================================================ */

    // Tính target cho từng bữa dựa theo % kcal và áp dụng rule Nutrient
    public static Nutrition approxMacroTargetForMeal(
            Nutrition dayTarget,
            double pctKcal,
            List<NutritionRule> rules,
            int weightKg,
            MealPlanCreationRequest request) {
        double kcal = safeDouble(dayTarget.getKcal()) * pctKcal;  // kcal mục tiêu cho bữa
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

    // Tính dinh dưỡng còn thiếu = target - consumed (có thể âm)
    public static Nutrition subNutSigned(Nutrition target, Nutrition consumed) {
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
    /* ========================================== DINH DƯỠNG BỮA ================================================ */







    // Tính kcal theo mục tiêu (ngày)
    public static double calculateTargetKcal(double tdee, ProfileCreationRequest profile) {
        final double MAX_DAILY_ADJ = 1000.0;    // ±1000 kcal/ngày
        final double MIN_KCAL_FEMALE = 1200.0;  // Mức kcal tối thiểu nữ
        final double MIN_KCAL_MALE = 1500.0;    // Mức kcal tối thiểu nam

        Integer deltaKg = profile.getTargetWeightDeltaKg();
        Integer weeks = profile.getTargetDurationWeeks();

        double dailyAdj = 0.0;  //Số kcal cần điều chỉnh
        boolean hasDelta = (deltaKg != null && deltaKg != 0) && (weeks != null && weeks > 0);
        if (hasDelta && profile.getGoal() != GoalType.MAINTAIN) {
            dailyAdj = (deltaKg * 7700.0) / (weeks * 7.0);
            dailyAdj = Math.max(-MAX_DAILY_ADJ, Math.min(MAX_DAILY_ADJ, dailyAdj));
        }
        double targetCalories = (profile.getGoal() == GoalType.MAINTAIN) ? tdee : (tdee + dailyAdj);

        // Mức kcal tối thiểu theo giới tính
        targetCalories = (profile.getGender() == Gender.MALE)
                ? Math.max(MIN_KCAL_MALE, targetCalories)
                : Math.max(MIN_KCAL_FEMALE, targetCalories);

        return targetCalories;
    }

    // Tính dinh dưỡng (ngày)
    public static Nutrition caculateNutrition(ProfileCreationRequest profile, AggregateConstraints agg) {
        final double FAT_PCT = 0.30; // WHO: chat beo ≤30%
        final double FREE_SUGAR_PCT_MAX = 0.10; // WHO: <10%
        final int SODIUM_MG_LIMIT = 2000; // WHO: <2000 mg natri/ngày

        int weight = profile.getWeightKg();

        // 1) Tính TDEE
        double tdee = caculateTDEE(profile);

        // 2) Tính kcal mục tiêu / ngày
        double targetCalories = calculateTargetKcal(tdee, profile);

        // 3.1) Protein (Đạm) theo g/kg
        double proteinPerKg =
                switch (profile.getGoal()) {
                    case MAINTAIN -> 0.8;
                    case LOSE -> 1.0;
                    case GAIN -> 1.2;
                };
        double proteinG = weight * proteinPerKg;
        double proteinKcal = proteinG * 4.0;

        // 3.2) Fat: 30% năng lượng (NẾU BÉO PHÌ NHỎ HƠN 30%)
        double fatKcal = targetCalories * FAT_PCT;
        double fatG = fatKcal / 9.0;

        // 3.3) Carb = phần còn lại
        double carbKcal = Math.max(0.0, targetCalories - proteinKcal - fatKcal);
        double carbG = carbKcal / 4.0;

        // 3.4) Fiber: tối thiểu 25g (nâng theo 14g/1000kcal nếu cần)
        double fiberG = Math.max(25.0, 14.0 * (targetCalories / 1000.0));

        // 3.5) Free sugar trần <10% năng lượng → g → mg
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

    // Áp dụng ràng buộc nutrient lên target dinh dưỡng
    public static Nutrition applyAggregateConstraintsToDayTarget(Nutrition target, AggregateConstraints a) {
        BigDecimal protein = target.getProteinG();
        BigDecimal carb = target.getCarbG();
        BigDecimal fat = target.getFatG();
        BigDecimal fiber = target.getFiberG();
        BigDecimal sodium = target.getSodiumMg();
        BigDecimal sugar = target.getSugarMg();

        // Protein (g/day)
        if (a.dayProteinMin != null && protein != null && protein.compareTo(a.dayProteinMin) < 0)
            protein = a.dayProteinMin;
        if (a.dayProteinMax != null && protein != null && protein.compareTo(a.dayProteinMax) > 0)
            protein = a.dayProteinMax;

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


    public static boolean isWithinRatio(double actual, double target, double minRatio, double maxRatio) {
        if (target <= 0) {
            return Math.abs(actual) <= 1e-6;
        }
        double ratio = actual / target;
        return ratio >= minRatio && ratio <= maxRatio;
    }

    public static boolean isSatisfiedSlot(Nutrition remaining, Nutrition slotTarget) {
        double tK  = safeDouble(slotTarget.getKcal());
        double tP  = safeDouble(slotTarget.getProteinG());
        double tC  = safeDouble(slotTarget.getCarbG());
        double tF  = safeDouble(slotTarget.getFatG());
        double tFi = safeDouble(slotTarget.getFiberG());

        double rK  = safeDouble(remaining.getKcal());
        double rP  = safeDouble(remaining.getProteinG());
        double rC  = safeDouble(remaining.getCarbG());
        double rF  = safeDouble(remaining.getFatG());
        double rFi = safeDouble(remaining.getFiberG());

        // actual = target - remaining
        double aK  = tK  - rK;
        double aP  = tP  - rP;
        double aC  = tC  - rC;
        double aF  = tF  - rF;
        double aFi = tFi - rFi;

        boolean kcalOk;
        if (tK < 80) {
            kcalOk = Math.abs(rK) <= EPS_KCAL;
        } else {
            kcalOk = isWithinRatio(aK, tK, KCAL_MIN_RATIO, KCAL_MAX_RATIO);
        }
        boolean protOk;
        if (tP < 5) {
            protOk = Math.abs(rP) <= EPS_PROT;
        } else {
            protOk = isWithinRatio(aP, tP, PROT_MIN_RATIO, PROT_MAX_RATIO);
        }
        boolean carbOk;
        if (tC < 10) {
            carbOk = Math.abs(rC) <= EPS_CARB;
        } else {
            carbOk = isWithinRatio(aC, tC, CARB_MIN_RATIO, CARB_MAX_RATIO);
        }
        boolean fatOk;
        if (tF < 5) {
            fatOk = Math.abs(rF) <= EPS_FAT;
        } else {
            fatOk = isWithinRatio(aF, tF, FAT_MIN_RATIO, FAT_MAX_RATIO);
        }
        boolean fiberOk;
        if (tFi < 5) {
            fiberOk = Math.abs(rFi) <= EPS_FIBER;
        } else {
            fiberOk = isWithinRatio(aFi, tFi, FIBER_MIN_RATIO, FIBER_MAX_RATIO);
        }
        return kcalOk && protOk && carbOk && fatOk && fiberOk;
    }

    // Độ lệch vector (L1) với trọng số cho 5 chất chính
    public static double nutritionDistance(Nutrition rem) {
        double wK  = 1.0 / Math.max(1.0, EPS_KCAL);
        double wP  = 1.0 / Math.max(1.0, EPS_PROT);
        double wC  = 1.0 / Math.max(1.0, EPS_CARB);
        double wF  = 1.0 / Math.max(1.0, EPS_FAT);
        double wFi = 3.0 / Math.max(1.0, EPS_FIBER); // ↑ ưu tiên fiber hơn

        return wK  * Math.abs(safeDouble(rem.getKcal()))
                + wP  * Math.abs(safeDouble(rem.getProteinG()))
                + wC  * Math.abs(safeDouble(rem.getCarbG()))
                + wF  * Math.abs(safeDouble(rem.getFatG()))
                + wFi * Math.abs(safeDouble(rem.getFiberG()));
    }

    // Helper kiểm tra sau khi thêm snap có vượt ngưỡng protein cho bữa không
    public static boolean wouldExceedProteinForMeal(
            Nutrition mealTarget,
            Nutrition remaining,
            Nutrition snap,
            double maxRatio
    ) {
        double tP = safeDouble(mealTarget.getProteinG());
        if (tP <= 0) return false;

        double achievedP = tP - safeDouble(remaining.getProteinG());
        double newProtRatio = (achievedP + safeDouble(snap.getProteinG())) / tP;
        return newProtRatio > maxRatio;
    }


    //===================================== TÍNH ĐIỂM MÓN ĂN =====================================//
    // Tính điểm món được chọn
    public static double scoreFoodHeuristic(Food f, Nutrition slotTarget) {
        Nutrition n = f.getNutrition();
        if (n == null) return -1e9;

        // Lấy các giá trị dinh dưỡng của món
        double kcal = safeDouble(n.getKcal());
        double p   = safeDouble(n.getProteinG());
        double c   = safeDouble(n.getCarbG());
        double fat = safeDouble(n.getFatG());
        double sum = p + c + fat + 1e-6;  // tránh chia 0

        double fiber = safeDouble(n.getFiberG());
        double sodium  = safeDouble(n.getSodiumMg());
        double sugarMg = safeDouble(n.getSugarMg());

        // Lấy các giá trị dinh dưỡng mục tiêu của slot
        double tp = safeDouble(slotTarget.getProteinG());
        double tc = safeDouble(slotTarget.getCarbG());
        double tf = safeDouble(slotTarget.getFatG());
        double sumT = tp + tc + tf + 1e-6;

        double rp  = p   / sum;
        double rc  = c   / sum;
        double rf  = fat / sum;

        double rtp = tp / sumT;
        double rtc = tc / sumT;
        double rtf = tf / sumT;

        // Tính penalty theo lệch tỷ lệ (càng nhỏ càng tốt)
        double ratioPenalty = Math.abs(rp - rtp) + Math.abs(rc - rtc) + Math.abs(rf - rtf);

        // Tính điểm kcal density (càng gần nửa target càng tốt)
        double kcalDensityScore = -Math.abs(kcal - (safeDouble(slotTarget.getKcal()) / 2.0));

        double fiberBonus = Math.min(fiber, 10.0);

        double sodiumPenalty = 0.0;
        if (safeDouble(slotTarget.getSodiumMg()) > 0) {
            sodiumPenalty = sodium / (safeDouble(slotTarget.getSodiumMg()) + 1e-6) * 2.0;
        }

        double sugarPenalty = 0.0;
        if (slotTarget.getSugarMg() != null && slotTarget.getSugarMg().doubleValue() > 0) {
            sugarPenalty = sugarMg / (slotTarget.getSugarMg().doubleValue() + 1e-6) * 1.5;
        }

        double extraProtPenalty = 0.0;
        if (tp > 0) {
            double overRatio = p / (tp + 1e-6);
            if (overRatio > 1.0) {
                extraProtPenalty = (overRatio - 1.0) * 4.0;
            }
        }

        return -ratioPenalty                     // càng khớp P/C/F càng tốt
                + (kcalDensityScore / 300.0)     // kcal khoảng 1/2 target thì đẹp
                + (fiberBonus * 0.4)             // thưởng xơ
                - sodiumPenalty                  // phạt mặn
                - sugarPenalty                   // phạt ngọt
                - extraProtPenalty;              // phạt dư đạm

    }
    //===================================== TÍNH ĐIỂM MÓN ĂN =====================================//

}
