package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.MealPlanItemCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.MealPlanItemResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface MealPlanItemService {
    MealPlanItemResponse createMealPlanItems(MealPlanItemCreationRequest request);
    Page<FoodResponse> getUpcomingFoods(int page, int size);
//    void smartSwapMealItem(UUID itemId);
}
