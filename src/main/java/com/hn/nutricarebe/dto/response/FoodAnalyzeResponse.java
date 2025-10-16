package com.hn.nutricarebe.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class FoodAnalyzeResponse{
    String name;                 // "meal salad"
    BigDecimal servingGram;      // 171.33
    NutritionResponse nutrition; // kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg ỨNG VỚI servingGram
    List<String> ingredients;    // ["lettuce","spinach",...,"salt"]
    Double confidence;           // 0.0..1.0
}
