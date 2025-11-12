package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.response.SwapSuggestion;

import java.util.List;
import java.util.UUID;

public interface MealPlanItemService {
    void smartSwapMealItem(UUID itemId);
    List<SwapSuggestion> suggest();
}
