package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientEstimateAI {
    String name;                  // tên AI nhìn thấy
    Double amountGram;            // lượng (gram/ml)
    Nutrition estimatedPer100;    // dinh dưỡng /100g ước tính (có thể null)
}
