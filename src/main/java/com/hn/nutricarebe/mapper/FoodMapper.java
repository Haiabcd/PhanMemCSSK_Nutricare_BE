package com.hn.nutricarebe.mapper;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.*;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.RecipeIngredientResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.entity.RecipeIngredient;

@Mapper(
        componentModel = "spring",
        uses = {NutritionMapper.class, TagMapper.class},
        imports = {HashSet.class, Collections.class, Collectors.class})
public interface FoodMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "ingredients", ignore = true)
    @Mapping(target = "loggedByUsers", ignore = true)
    @Mapping(target = "inMealPlanItems", ignore = true)
    @Mapping(target = "isIngredient", ignore = true)
    @Mapping(target = "tags", ignore = true)
    Food toFood(FoodCreationRequest req);

    @Mapping(target = "imageUrl", expression = "java(cdnHelper.buildUrl(food.getImageKey()))")
    @Mapping(
            target = "mealSlots",
            expression =
                    "java(food.getMealSlots() != null ? new HashSet<>(food.getMealSlots()) : Collections.emptySet())")
    @Mapping(target = "ingredients", expression = "java(toRecipeIngredientResponses(food.getIngredients(), cdnHelper))")
    FoodResponse toFoodResponse(Food food, @Context CdnHelper cdnHelper);

    default Set<RecipeIngredientResponse> toRecipeIngredientResponses(
            Set<RecipeIngredient> set, @Context CdnHelper cdnHelper) {
        if (set == null || set.isEmpty()) return Collections.emptySet();

        return set.stream()
                .map(ri -> {
                    Ingredient ing = ri.getIngredient();
                    String imageUrl = null;
                    if (ing != null
                            && ing.getImageKey() != null
                            && !ing.getImageKey().isBlank()) {
                        imageUrl = cdnHelper.buildUrl(ing.getImageKey());
                    }
                    return RecipeIngredientResponse.builder()
                            .ingredientId(ing != null ? ing.getId() : null)
                            .name(ing != null ? ing.getName() : null)
                            .unit(
                                    ing != null && ing.getUnit() != null
                                            ? ing.getUnit().name()
                                            : null)
                            .imageUrl(imageUrl)
                            .quantity(ri.getQuantity())
                            .build();
                })
                .collect(Collectors.toSet());
    }
}
