package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.PlanLogManualRequest;
import com.hn.nutricarebe.dto.request.PlanLogUpdateRequest;
import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.enums.MealSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PlanLogService {
    void savePlanLog(SaveLogRequest req);
    List<LogResponse> getLog(LocalDate date, MealSlot mealSlot);
    void deleteById(UUID id);
    NutritionResponse getNutritionLogByDate(LocalDate date);
    KcalWarningResponse savePlanLog_Manual(PlanLogManualRequest req);
    KcalWarningResponse updatePlanLog( PlanLogUpdateRequest req, UUID id);
    List<TopFoodDto> getTopFoods(UUID userId, LocalDate start, LocalDate end, int limit);
    List<DailyNutritionDto> getDailyNutrition(UUID userId, LocalDate start, LocalDate end, boolean fillMissingDays);
    Map<MealSlot, Map<String, Long>> getMealSlotSummary(UUID userId, LocalDate start, LocalDate end);
    List<DayConsumedTotal> getConsumedTotalsBetween(LocalDate from, LocalDate to, UUID userId);
}