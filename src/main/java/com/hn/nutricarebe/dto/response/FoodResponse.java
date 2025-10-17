package com.hn.nutricarebe.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
//@JsonInclude(JsonInclude.Include.NON_NULL)
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
    Set<String> tags;
}
