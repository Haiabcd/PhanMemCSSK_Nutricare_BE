package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.response.DayTarget;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.enums.MealSlot;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface MealPlanDayService {
    MealPlanResponse createPlan(MealPlanCreationRequest request, int number);
    MealPlanResponse getMealPlanByDate(LocalDate date);
    void removeFromDate(LocalDate today, UUID userId);
    MealPlanResponse updatePlanForOneDay(LocalDate date, UUID userId);
    double getMealTargetKcal(UUID userId, MealSlot slot);
    List<DayTarget> getDayTargetsBetween(LocalDate from, LocalDate to, UUID userId);
}