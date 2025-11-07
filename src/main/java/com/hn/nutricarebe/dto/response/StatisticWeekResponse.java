package com.hn.nutricarebe.dto.response;

import java.util.List;
import java.util.Map;

import com.hn.nutricarebe.enums.MealSlot;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatisticWeekResponse {
    Integer weightKg;
    String bmiClassification;
    double bmi;
    List<TopFoodDto> topFoods;
    List<DailyNutritionDto> dailyNutrition;
    Map<MealSlot, Map<String, Long>> mealSlotSummary;
    List<DailyWaterTotalDto> dailyWaterTotals;
    List<String> warnings;
}
