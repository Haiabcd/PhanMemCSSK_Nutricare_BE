package com.hn.nutricarebe.helper;

import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.entity.PlanLog;

import java.math.BigDecimal;
import java.util.List;

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
}
