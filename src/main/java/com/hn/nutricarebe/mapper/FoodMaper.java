package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
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


    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void patch(@MappingTarget Food entity, FoodPatchRequest req);

    // Sau khi patch, (null = giữ nguyên; [] = clear)
    @AfterMapping
    default void afterPatch(@MappingTarget Food entity, FoodPatchRequest req) {
        if (req.getMealSlots() != null) {
            entity.getMealSlots().clear();
            entity.getMealSlots().addAll(req.getMealSlots());
        }
        if (req.getTags() != null) {
            entity.getTags().clear();
            entity.getTags().addAll(req.getTags());
        }
    }


    @Mapping(target = "imageUrl", expression = "java(cdnHelper.buildUrl(food.getImageKey()))")
    @Mapping(target = "createdById", expression = "java(food.getCreatedBy() != null ? food.getCreatedBy().getId() : null)")
    FoodResponse toFoodResponse(Food food, @Context CdnHelper cdnHelper);
}
