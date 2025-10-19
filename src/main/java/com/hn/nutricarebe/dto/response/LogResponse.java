package com.hn.nutricarebe.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hn.nutricarebe.enums.MealSlot;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogResponse {
    UUID id;
    MealSlot mealSlot;
    FoodResponse food;
    String nameFood;
    BigDecimal portion;
    NutritionResponse actualNutrition;
}
