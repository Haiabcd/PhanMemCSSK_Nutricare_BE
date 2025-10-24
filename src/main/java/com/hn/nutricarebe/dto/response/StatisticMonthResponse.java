package com.hn.nutricarebe.dto.response;

import com.hn.nutricarebe.enums.GoalType;
import com.hn.nutricarebe.enums.MealSlot;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatisticMonthResponse {
    Integer weightKg;
    String bmiClassification;
    double bmi;
    List<TopFoodDto> topFoods;
    List<MonthlyWeeklyNutritionDto> weeklyNutrition;
    Map<MealSlot, Map<String, Long>> mealSlotSummary;
    List<MonthlyWeeklyWaterTotalDto> weeklyWaterTotals;
    // danh sách các câu cảnh báo
    List<String> warnings;
}
