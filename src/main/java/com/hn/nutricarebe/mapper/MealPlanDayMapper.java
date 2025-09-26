package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.MealPlanDay;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { NutritionMapper.class })
public interface MealPlanDayMapper {
    MealPlanResponse toMealPlanResponse(MealPlanDay mealPlanDay);
}
