package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.response.MealPlanItemResponse;
import com.hn.nutricarebe.entity.MealPlanItem;
import com.hn.nutricarebe.enums.MealSlot;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;


@Mapper(
        componentModel = "spring",
        uses = { FoodMapper.class, NutritionMapper.class } )
public interface MealPlanItemMapper {
    @Mapping(source = "mealSlot", target = "mealSlot") // Enum -> String
    MealPlanItemResponse toMealPlanItemResponse(MealPlanItem mealPlanItem, @Context CdnHelper cdnHelper);

    // Enum -> String (MapStruct sẽ tự gọi)
    default String map(MealSlot slot) {
        return slot != null ? slot.name() : null;
    }

}
