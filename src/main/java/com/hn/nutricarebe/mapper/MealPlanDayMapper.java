package com.hn.nutricarebe.mapper;


import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.MealPlanDay;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;




@Mapper(
        componentModel = "spring",
        uses = { MealPlanItemMapper.class }
)
public interface MealPlanDayMapper {
    @Mapping(source = "user.id", target = "user")
    MealPlanResponse toMealPlanResponse(MealPlanDay mealPlanDay, @Context CdnHelper cdnHelper);


}
