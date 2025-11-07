package com.hn.nutricarebe.service;

import java.util.List;
import java.util.UUID;

import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.enums.MealSlot;

public interface MealPlanItemService {
    void smartSwapMealItem(UUID itemId);

    List<FoodResponse> suggestAllowedFoodsInternal(MealSlot slot, int limit);
}
