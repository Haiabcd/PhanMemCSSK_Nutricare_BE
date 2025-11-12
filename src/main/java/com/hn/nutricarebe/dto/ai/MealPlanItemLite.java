package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.dto.response.NutritionResponse;
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
public class MealPlanItemLite {
    UUID itemId;
    String slot;
    UUID currentFoodId;
    String currentFoodCode;      // nếu có
    String currentFoodName;
    BigDecimal portion;          // khẩu phần của item
    NutritionResponse nutrition;         // SNAPSHOT đã nhân khẩu phần: kcal/proteinG/carbG/fatG/fiberG/sodiumMg/sugarMg
}
