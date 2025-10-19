package com.hn.nutricarebe.service.impl;


import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.PlanOrchestrator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class PlanOrchestratorImpl implements PlanOrchestrator {
    MealPlanDayService mealPlanDayService;

    @Override
    public void updatePlan(LocalDate date, UUID userId) {
       mealPlanDayService.updatePlanForOneDay(date, userId);
        LocalDate tomorrow = date.plusDays(1);
        mealPlanDayService.removeFromDate(tomorrow, userId);
    }
}
