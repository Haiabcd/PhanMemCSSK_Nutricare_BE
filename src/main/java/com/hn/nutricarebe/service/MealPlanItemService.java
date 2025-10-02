package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.MealPlanItemCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanItemResponse;

public interface MealPlanItemService {
    public MealPlanItemResponse createMealPlanItems(MealPlanItemCreationRequest request);
}
