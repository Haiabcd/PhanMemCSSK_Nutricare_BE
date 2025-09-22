package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = { NutritionMapper.class })
public interface FoodMaper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)

    @Mapping(target = "ingredients", ignore = true)
    @Mapping(target = "savedByUsers", ignore = true)
    @Mapping(target = "loggedByUsers", ignore = true)
    @Mapping(target = "inMealPlanItems", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    Food toFood(FoodCreationRequest req);

    @Mapping(target = "imageUrl", expression = "java(cdnHelper.buildUrl(food.getImageKey()))")
    @Mapping(target = "createdById", expression = "java(food.getCreatedBy() != null ? food.getCreatedBy().getId() : null)")
    FoodResponse toFoodResponse(Food food, @Context CdnHelper cdnHelper);
}
