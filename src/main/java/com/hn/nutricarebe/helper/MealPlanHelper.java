package com.hn.nutricarebe.helper;


import com.hn.nutricarebe.entity.Nutrition;

import java.math.BigDecimal;
import java.math.RoundingMode;


public final class MealPlanHelper {
    private MealPlanHelper() {}

    public static final double[] PORTION_STEPS = {1.5, 1.0, 0.5};

    public static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
    public static double safeDouble(BigDecimal x){
        return x == null ? 0.0 : x.doubleValue();
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
}
