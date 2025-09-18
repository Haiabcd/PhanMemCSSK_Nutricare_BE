package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import org.mapstruct.*;

import java.util.HashSet;

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
    Food toFood(FoodCreationRequest req, @Context UserResolver userResolver);

    FoodResponse toFoodResponse(Food food);


    @AfterMapping
    default void afterToFood(FoodCreationRequest req,
                             @Context UserResolver userResolver,
                             @MappingTarget Food food) {
        food.setCreatedBy(userResolver.mustExist(req.getCreatedBy()));
        if (food.getMealSlots() == null) {
            food.setMealSlots(new HashSet<>());
        }
        if (food.getTags() == null) {
            food.setTags(new HashSet<>());
        }
    }

    @AfterMapping
    default void afterToFoodResponse(Food food, @MappingTarget FoodResponse res) {
        if (food.getCreatedBy() != null) {
            res.setCreatedById(food.getCreatedBy().getId());
        }
    }

}
