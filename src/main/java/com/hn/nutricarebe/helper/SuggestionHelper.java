package com.hn.nutricarebe.helper;


import com.hn.nutricarebe.dto.ai.*;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.Nutrition;


import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.hn.nutricarebe.helper.MealPlanHelper.safeDouble;


public final class SuggestionHelper {
    private SuggestionHelper() {}

    public static double d(BigDecimal x) { return x == null ? 0.0 : x.doubleValue(); }

    public static final double[] PORTIONS = {1.5, 1.0, 0.5};
    public static final int TOP_K = 3;

    public static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    public static boolean isGoodEnough(double score, NutritionResponse target) {
        double kcal = Math.max(1.0, d(target.getKcal()));
        return score < (0.05 * kcal + 20.0); // cùng logic smartSwap cũ
    }

    public static boolean isGoodEnoughStrict(double score, Nutrition target) {
        double kcal = Math.max(1.0, safeDouble(target.getKcal()));
        // Ngưỡng động theo kcal nhưng mềm hơn: 0.02 * kcal + 10, tối đa 20
        double dynamicThreshold = 0.02 * kcal + 10.0;
        double threshold = Math.min(dynamicThreshold, 20.0);
        return score <= threshold;
    }

    public static Nutrition scale(Nutrition base, double portion) {
        return Nutrition.builder()
                .kcal(bd(d(base.getKcal()) * portion))
                .proteinG(bd(d(base.getProteinG()) * portion))
                .carbG(bd(d(base.getCarbG()) * portion))
                .fatG(bd(d(base.getFatG()) * portion))
                .fiberG(bd(d(base.getFiberG()) * portion))
                .sodiumMg(bd(d(base.getSodiumMg()) * portion))
                .sugarMg(bd(d(base.getSugarMg()) * portion))
                .build();
    }

    // khoảng cách L1 có trọng số (ưu tiên kcal)
    public static double dist(Nutrition a, NutritionResponse b) {
        double dk = Math.abs(d(a.getKcal())     - d(b.getKcal()));
        double dp = Math.abs(d(a.getProteinG()) - d(b.getProteinG()));
        double dc = Math.abs(d(a.getCarbG())    - d(b.getCarbG()));
        double df = Math.abs(d(a.getFatG())     - d(b.getFatG()));
        return dk + dp*0.4 + dc*0.3 + df*0.3;
    }

    public static String buildImageUrl(String key, String cdnBaseUrl) {
        if (key == null || key.isBlank()) return null;
        if (cdnBaseUrl == null || cdnBaseUrl.isBlank()) return key;
        boolean baseEndsWithSlash = cdnBaseUrl.endsWith("/");
        boolean keyStartsWithSlash = key.startsWith("/");
        if (baseEndsWithSlash && keyStartsWithSlash) {
            return cdnBaseUrl + key.substring(1);
        } else if (!baseEndsWithSlash && !keyStartsWithSlash) {
            return cdnBaseUrl + "/" + key;
        } else {
            return cdnBaseUrl + key;
        }
    }


}
