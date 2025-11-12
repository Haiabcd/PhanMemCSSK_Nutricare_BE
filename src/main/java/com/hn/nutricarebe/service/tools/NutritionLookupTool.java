package com.hn.nutricarebe.service.tools;

import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NutritionLookupTool {
    private final FoodRepository foodRepository;
    private final IngredientRepository ingredientRepository;

    /**
     * Tra cứu dinh dưỡng theo tên món ăn hoặc nguyên liệu.
     * - Ưu tiên FOOD (dinh dưỡng theo khẩu phần mặc định nếu có).
     * - Nếu không có FOOD, thử INGREDIENT (trả per100).
     * - Nếu vẫn không thấy, trả kind="UNKNOWN" + gợi ý gần giống (alternatives).
     */
    @Tool(
            name = "lookupNutritionByName",
            description = "Tra cứu dinh dưỡng cho 'name' từ CSDL. Nếu là món ăn (FOOD) sẽ trả dinh dưỡng theo 1 khẩu phần; " +
                    "nếu là nguyên liệu (INGREDIENT) sẽ trả per100. Nếu không có, trả UNKNOWN kèm suggestions."
    )
    public Map<String, Object> lookupNutritionByName(String name, Boolean includeAlternatives) {
        log.error(name);
        String q = norm(name);
        if (q.isBlank()) {
            return Map.of("kind", "UNKNOWN", "query", name, "source", "NONE");
        }

        // 1) FOOD
        Optional<Food> food = findFood(q);
        if (food.isPresent()) {
            Food f = food.get();

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", "FOOD");
            out.put("query", name);
            out.put("matchedName", f.getName());
            out.put("source", "DB");
            out.put("nutrition", toNutritionDTO(f.getNutrition())); // ✅ DTO thuần
            out.put("servingName", f.getServingName());
            out.put("servingGram", toD(f.getServingGram()));
            out.put("defaultServing", f.getDefaultServing());
            // tags → list thuần
            List<String> tagCodes = (f.getTags() == null) ? List.of()
                    : f.getTags().stream().map(t -> t.getNameCode()).toList();
            out.put("tags", tagCodes);
            return out;
        }

        // 2) INGREDIENT
        Optional<Ingredient> ing = findIngredient(q);
        if (ing.isPresent()) {
            Ingredient i = ing.get();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("kind", "INGREDIENT");
            out.put("query", name);
            out.put("matchedName", i.getName());
            out.put("source", "DB");
            out.put("per100", toNutritionDTO(i.getPer100())); // ✅ DTO thuần

            // aliases → list thuần (tránh PersistentSet)
            List<String> aliasList = (i.getAliases() == null) ? List.of() : new ArrayList<>(i.getAliases());
            // Nếu aliases là entity, map sang tên: i.getAliases().stream().map(AliasEntity::getName).toList();
            out.put("aliases", aliasList);
            return out;
        }

        // 3) UNKNOWN + alternatives
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", "UNKNOWN");
        out.put("query", name);
        out.put("source", "NONE");

        if (Boolean.TRUE.equals(includeAlternatives)) {
            var altFoods = foodRepository.searchByNameUnaccent(q, PageRequest.of(0, 3))
                    .map(Food::getName).getContent();
            var altIngsPage = ingredientRepository.searchByNameOrAlias(q, PageRequest.of(0, 3));
            var altIngs = altIngsPage.getContent().stream().map(Ingredient::getName).toList();

            List<String> suggestions = new ArrayList<>();
            suggestions.addAll(altFoods);
            suggestions.addAll(altIngs);
            out.put("alternatives", suggestions);
        }
        return out;
    }

    // ===== helpers =====
    private Map<String, Object> toNutritionDTO(Nutrition n) {
        if (n == null) return Map.of();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kcal",     toD(n.getKcal()));
        m.put("proteinG", toD(n.getProteinG()));
        m.put("carbG",    toD(n.getCarbG()));
        m.put("fatG",     toD(n.getFatG()));
        m.put("fiberG",   toD(n.getFiberG()));
        m.put("sodiumMg", toD(n.getSodiumMg()));
        m.put("sugarMg",  toD(n.getSugarMg()));
        return m;
    }
    private Optional<Food> findFood(String q){
        var page = foodRepository.searchByNameUnaccent(q, PageRequest.of(0, 1));
        if (!page.isEmpty()) return Optional.of(page.getContent().get(0));
        return Optional.empty();
    }

    private Optional<Ingredient> findIngredient(String q){
        return ingredientRepository.findByNameIgnoreCase(q)
                .or(() -> ingredientRepository.findByAliasIgnoreCase(q))
                .or(() -> {
                    var p = ingredientRepository.searchByNameOrAlias(q, PageRequest.of(0, 1));
                    return p.isEmpty() ? Optional.empty() : Optional.of(p.getContent().get(0));
                });
    }

    private static Double toD(java.math.BigDecimal x){ return x == null ? null : x.doubleValue(); }
    private String norm(String s){ return s==null? "" : s.trim(); }
}
