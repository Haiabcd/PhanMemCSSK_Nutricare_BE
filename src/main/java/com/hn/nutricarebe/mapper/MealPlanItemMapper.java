package com.hn.nutricarebe.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.hn.nutricarebe.dto.response.MealPlanItemResponse;
import com.hn.nutricarebe.entity.MealPlanItem;
import com.hn.nutricarebe.enums.MealSlot;

@Mapper(
        componentModel = "spring",
        uses = {FoodMapper.class, NutritionMapper.class})
public interface MealPlanItemMapper {
    @Mapping(source = "mealSlot", target = "mealSlot") // Enum -> String
    MealPlanItemResponse toMealPlanItemResponse(MealPlanItem mealPlanItem, @Context CdnHelper cdnHelper);

    default String map(MealSlot slot) {
        return slot != null ? slot.name() : null;
    }
}
