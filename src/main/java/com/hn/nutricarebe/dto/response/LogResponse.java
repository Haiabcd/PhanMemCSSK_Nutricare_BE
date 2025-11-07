package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import com.hn.nutricarebe.enums.MealSlot;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LogResponse {
    UUID id;
    MealSlot mealSlot;
    FoodResponse food;
    String nameFood;
    BigDecimal portion;
    NutritionResponse actualNutrition;
    Set<PlanLogIngredientResponse> ingredients;
}
