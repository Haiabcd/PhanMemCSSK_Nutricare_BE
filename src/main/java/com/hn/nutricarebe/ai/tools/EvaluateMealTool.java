package com.hn.nutricarebe.ai.tools;

import com.hn.nutricarebe.dto.request.MealEvalItem;
import com.hn.nutricarebe.dto.response.MealEvaluationDto;
import com.hn.nutricarebe.service.FoodService;
import com.hn.nutricarebe.service.ProfileService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EvaluateMealTool implements Tool{
    FoodService foodService;
//    NutritionRuleService ruleService;
    ProfileService profileService;


    @Override
    public String name() { return "evaluate_meal"; }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> args) {
        String userId = (String) args.get("userId");
        List<Map<String,Object>> itemsRaw = (List<Map<String,Object>>) args.get("items");
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("userId is required");
        if (itemsRaw == null || itemsRaw.isEmpty()) throw new IllegalArgumentException("items is required");

        // 1) Chuẩn hoá items
        List<MealEvalItem> items = new ArrayList<>();
        for (var it : itemsRaw) {
            MealEvalItem x = new MealEvalItem();
            x.setFoodName((String) it.get("foodName"));
            Object g = it.get("grams");
            x.setGrams(g instanceof Number ? ((Number) g).doubleValue() : null);
            items.add(x);
        }

        // 2) Tính dinh dưỡng (tuỳ FoodService của bạn)
        //    - you may need: resolveByName -> to foodId, then sum nutrition
//        var nutrition = foodService.calculateNutritionByNames(items); // gợi ý hàm: trả kcal, protein, carbs, fat, fiber...
//
//        // 3) Check rule cho user
//        UserProfile profile = profileService.getByUserId(userId);
//        List<String> warnings = ruleService.checkViolations(profile, nutrition);
//
//        MealEvaluationDto dto = new MealEvaluationDto();
//        dto.setKcal((Double) nutrition.get("kcal"));
//        dto.setProtein((Double) nutrition.get("protein"));
//        dto.setCarbs((Double) nutrition.get("carbs"));
//        dto.setFat((Double) nutrition.get("fat"));
//        dto.setFiber((Double) nutrition.getOrDefault("fiber", null));
//        dto.setWarnings(warnings);

        MealEvaluationDto dto = new MealEvaluationDto();
        return Map.of("evaluation", dto);
    }
}
