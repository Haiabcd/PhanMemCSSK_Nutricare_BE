package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import org.mapstruct.*;
import java.util.HashSet;
import java.util.Collections;
import java.util.stream.Collectors;

@Mapper(
        componentModel = "spring",
        uses = { NutritionMapper.class },
        imports = { HashSet.class, Collections.class, Collectors.class }
)
public interface FoodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ingredients", ignore = true)
    @Mapping(target = "savedByUsers", ignore = true)
    @Mapping(target = "loggedByUsers", ignore = true)
    @Mapping(target = "inMealPlanItems", ignore = true)
    Food toFood(FoodCreationRequest req);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void patch(@MappingTarget Food entity, FoodPatchRequest req);

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
    @Mapping(target = "tags",
            expression = "java(food.getTags() != null ? " +
                    "food.getTags().stream().map(t -> t.getNameCode()).collect(Collectors.toSet()) : " +
                    "Collections.emptySet())")
    @Mapping(target = "mealSlots",
            expression = "java(food.getMealSlots() != null ? new HashSet<>(food.getMealSlots()) : Collections.emptySet())")
    FoodResponse toFoodResponse(Food food, @Context CdnHelper cdnHelper);
}
