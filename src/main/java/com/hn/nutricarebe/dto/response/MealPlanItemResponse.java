package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MealPlanItemResponse {
    UUID id;
    String mealSlot;
    FoodResponse food;
    BigDecimal portion;
    Integer rank;
    String note;
    boolean used;
    boolean swapped;
    NutritionResponse nutrition;
}
