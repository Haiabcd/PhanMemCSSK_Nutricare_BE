package com.hn.nutricarebe.service.tools;

import com.hn.nutricarebe.dto.ai.IngredientEstimateAI;
import com.hn.nutricarebe.dto.ai.ResolvedIngredientAI;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class IngredientTool {
    private final IngredientRepository ingredientRepository;

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    @Tool(
            name = "resolveIngredients",
            description = "Nhận danh sách nguyên liệu (name, amountGram, estimatedPer100?) và map sang dữ liệu CSDL nếu có; "
                    + "nếu không khớp sẽ giữ estimatedPer100 (nếu có) và đánh dấu ESTIMATED."
    )
    public List<ResolvedIngredientAI> resolveIngredients(List<IngredientEstimateAI> items) {
        if (items == null || items.isEmpty()) return List.of();

        List<ResolvedIngredientAI> out = new ArrayList<>();
        for (var it : items) {
            if (it.getAmountGram() == null || it.getAmountGram() <= 0) continue;
            String q = norm(it.getName());
            if (q.isBlank()) continue;

            Optional<Ingredient> found =
                    ingredientRepository.findByNameIgnoreCase(q)
                            .or(() -> ingredientRepository.findByAliasIgnoreCase(q))
                            .or(() -> {
                                var page = ingredientRepository.searchByNameOrAlias(q, PageRequest.of(0, 1));
                                return page.isEmpty() ? Optional.empty() : Optional.of(page.getContent().getFirst());
                            });

            if (found.isPresent()) {
                Ingredient ing = found.get();
                out.add(ResolvedIngredientAI.builder()
                        .requestedName(it.getName())
                        .matchedName(ing.getName())
                        .source(ResolvedIngredientAI.Source.DB)
                        .amountGram(it.getAmountGram())
                        .per100(safeNutrition(ing.getPer100(), it.getEstimatedPer100()))
                        .build());
            } else {
                // Không có trong DB -> giữ estimated (nếu có), đánh dấu ESTIMATED
                out.add(ResolvedIngredientAI.builder()
                        .requestedName(it.getName())
                        .matchedName(null)
                        .source(ResolvedIngredientAI.Source.ESTIMATED)
                        .amountGram(it.getAmountGram())
                        .per100(safeNutrition(null, it.getEstimatedPer100()))
                        .build());
            }
        }
        return out;
    }

    @Tool(
            name = "sumNutritionFromResolved",
            description = "Tính tổng dinh dưỡng của danh sách nguyên liệu đã resolve (mỗi phần tử có per100 và amountGram). "
                    + "Đơn vị gram/ml – per100 nghĩa là dinh dưỡng trên 100g(ml)."
    )
    public NutritionResponse sumNutritionFromResolved(List<ResolvedIngredientAI> resolved) {
        if (resolved == null || resolved.isEmpty()) {
            return NutritionResponse.builder().build();
        }
        BigDecimal kcal=bd0(), protein=bd0(), carb=bd0(), fat=bd0(), fiber=bd0(), sodium=bd0(), sugar=bd0();

        for (var r : resolved) {
            Nutrition n100 = r.getPer100();
            Double amt = r.getAmountGram();
            if (n100 == null || amt == null || amt <= 0) continue;

            BigDecimal factor = BigDecimal.valueOf(amt).divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP);

            kcal   = kcal.add(n(n100.getKcal()).multiply(factor));
            protein= protein.add(n(n100.getProteinG()).multiply(factor));
            carb   = carb.add(n(n100.getCarbG()).multiply(factor));
            fat    = fat.add(n(n100.getFatG()).multiply(factor));
            fiber  = fiber.add(n(n100.getFiberG()).multiply(factor));
            sodium = sodium.add(n(n100.getSodiumMg()).multiply(factor));
            sugar  = sugar.add(n(n100.getSugarMg()).multiply(factor));
        }

        return NutritionResponse.builder()
                .kcal(kcal)
                .proteinG(protein)
                .carbG(carb)
                .fatG(fat)
                .fiberG(fiber)
                .sodiumMg(sodium)
                .sugarMg(sugar)
                .build();
    }

    // ===== helpers =====
    private String norm(String s){ return s==null? "": s.trim(); }
    private static BigDecimal n(BigDecimal v){ return v==null? bd0(): v; }
    private static BigDecimal bd0(){ return BigDecimal.ZERO; }

    private static Nutrition safeNutrition(Nutrition dbPer100, Nutrition estimatedPer100){
        if (dbPer100 != null) return dbPer100;
        return estimatedPer100;
    }
}
