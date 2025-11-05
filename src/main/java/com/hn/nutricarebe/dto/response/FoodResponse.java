package com.hn.nutricarebe.dto.response;

import com.hn.nutricarebe.dto.request.TagDto;
import com.hn.nutricarebe.enums.MealSlot;
import lombok.*;
import lombok.experimental.FieldDefaults;


import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FoodResponse {
    UUID id;
    String name;
    String description;
    String imageUrl;
    String servingName;
    BigDecimal servingGram;
    Integer defaultServing;
    Integer cookMinutes;
    NutritionResponse nutrition;
    boolean isIngredient;
    Set<MealSlot> mealSlots;
    Set<TagDto> tags;
    Set<RecipeIngredientResponse> ingredients;
}
