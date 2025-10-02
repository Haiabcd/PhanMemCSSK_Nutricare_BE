package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanResponse;

public interface MealPlanDayService {
    public MealPlanResponse createPlan(MealPlanCreationRequest request);
}
