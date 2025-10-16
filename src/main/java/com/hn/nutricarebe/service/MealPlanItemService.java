package com.hn.nutricarebe.service;




import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.enums.MealSlot;

import java.util.List;
import java.util.UUID;

public interface MealPlanItemService {
    void smartSwapMealItem(UUID itemId);
    List<FoodResponse> suggestAllowedFoodsInternal(MealSlot slot, int limit);
}
