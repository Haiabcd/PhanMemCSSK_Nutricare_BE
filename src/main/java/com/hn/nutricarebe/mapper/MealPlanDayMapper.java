package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.MealPlanDay;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = { NutritionMapper.class })
public interface MealPlanDayMapper {
    @Mapping(source = "user.id", target = "user")
    MealPlanResponse toMealPlanResponse(MealPlanDay mealPlanDay);
}
