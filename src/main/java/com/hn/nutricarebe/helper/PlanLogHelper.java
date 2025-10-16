package com.hn.nutricarebe.helper;

import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.entity.PlanLog;

import java.math.BigDecimal;
import java.util.List;

import static com.hn.nutricarebe.helper.MealPlanHelper.safeDouble;
import static com.hn.nutricarebe.helper.MealPlanHelper.scaleNutrition;

public final class  PlanLogHelper {
    private PlanLogHelper() {}

    public static NutritionResponse aggregateActual(List<PlanLog> logs) {
        BigDecimal kcal      = BigDecimal.ZERO;
        BigDecimal proteinG  = BigDecimal.ZERO;
        BigDecimal carbG     = BigDecimal.ZERO;
        BigDecimal fatG      = BigDecimal.ZERO;
        BigDecimal fiberG    = BigDecimal.ZERO;
        BigDecimal sodiumMg  = BigDecimal.ZERO;
        BigDecimal sugarMg   = BigDecimal.ZERO;

        for (PlanLog log : logs) {
            Nutrition n = log.getActualNutrition();
            if (n == null) continue;

            // null-safe add
            kcal     = kcal.add(n.getKcal()      != null ? n.getKcal()      : BigDecimal.ZERO);
            proteinG = proteinG.add(n.getProteinG()!= null ? n.getProteinG() : BigDecimal.ZERO);
            carbG    = carbG.add(n.getCarbG()    != null ? n.getCarbG()    : BigDecimal.ZERO);
            fatG     = fatG.add(n.getFatG()      != null ? n.getFatG()      : BigDecimal.ZERO);
            fiberG   = fiberG.add(n.getFiberG()   != null ? n.getFiberG()   : BigDecimal.ZERO);
            sodiumMg = sodiumMg.add(n.getSodiumMg()!= null ? n.getSodiumMg(): BigDecimal.ZERO);
            sugarMg  = sugarMg.add(n.getSugarMg() != null ? n.getSugarMg() : BigDecimal.ZERO);
        }

        return NutritionResponse.builder()
                .kcal(kcal)
                .proteinG(proteinG)
                .carbG(carbG)
                .fatG(fatG)
                .fiberG(fiberG)
                .sodiumMg(sodiumMg)
                .sugarMg(sugarMg)
                .build();
    }


    // Lấy dinh dưỡng thực tế từ log; nếu thiếu thì fallback từ món * portion
    public static Nutrition resolveActualOrFallback(PlanLog l) {
        if (l.getActualNutrition() != null && safeDouble(l.getActualNutrition().getKcal()) > 0) {
            return l.getActualNutrition();
        }
        if (l.getFood() != null && l.getFood().getNutrition() != null) {
            double portion = l.getPortion() == null ? 1.0 : l.getPortion().doubleValue();
            return scaleNutrition(l.getFood().getNutrition(), portion);
        }
        return new Nutrition();
    }
}
