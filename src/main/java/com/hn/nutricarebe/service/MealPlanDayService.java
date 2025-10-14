package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MealPlanDayService {
    MealPlanResponse createPlan(MealPlanCreationRequest request, int number);
    MealPlanResponse getMealPlanByDate(LocalDate date);
    void removeFromDate(LocalDate today, UUID userId);
}
