package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.response.SwapSuggestion;
import com.hn.nutricarebe.entity.MealPlanItem;

import java.util.List;
import java.util.UUID;

public interface MealPlanItemService {
    void smartSwapMealItem(UUID itemId);
    List<SwapSuggestion> suggest();
    void updateCache(UUID userId, MealPlanItem itemOld);
}
