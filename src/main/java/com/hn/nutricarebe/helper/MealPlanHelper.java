package com.hn.nutricarebe.helper;


import com.hn.nutricarebe.dto.TagDirectives;
import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.enums.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Year;
import java.util.*;


public final class MealPlanHelper {
    private MealPlanHelper() {}

    public static final double[] PORTION_STEPS = {1.5, 1.0, 0.5};

    public static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
    public static double safeDouble(BigDecimal x){
        return x == null ? 0.0 : x.doubleValue();
    }

    public static String safeStr(String s){ return s==null? "" : s.trim(); }

    //Tính BMI
    public static double caculateBMI(ProfileCreationRequest profile) {
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
        // Giới tính
        Gender ruleSex = r.getApplicableSex();
        if (ruleSex != null) {
            if (p.getGender() == null || p.getGender() != ruleSex) return true;
        }
        // Độ tuổi
        int currentYear = java.time.Year.now().getValue();
        int age = Math.max(0, currentYear - p.getBirthYear());
        if (r.getAgeMin() != null && age < r.getAgeMin()) return true;
        if (r.getAgeMax() != null && age > r.getAgeMax()) return true;
        return false;
    }

    // Lấy tập tag của món, tránh null
    public static Set<FoodTag> tagsOf(Food f) {
        return f.getTags() == null ? Collections.emptySet() : f.getTags();
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
            if (isApplicableToDemographics(r, request)) continue;

            Set<FoodTag> tags = r.getFoodTags();
            if (tags == null || tags.isEmpty()) continue;

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
    public static boolean passesItemRules(List<NutritionRule> rules, Food food, Nutrition snap, MealPlanCreationRequest request) {
        if (rules == null || rules.isEmpty()) return true;
        for (NutritionRule r : rules) {
            // Bỏ qua nếu rule không phải là từng món
            if (r.getScope() != RuleScope.ITEM) continue;
            if (isApplicableToDemographics(r, request)) continue;

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
}
