package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;


@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DishVisionResult {
    String dishName;
    String servingName;      // “phần”, “bát”, “đĩa”… (ngắn gọn)
    BigDecimal servingGram;  // gram/khẩu phần nếu ước tính được
    Nutrition nutrition;     // CHO 1 KHẨU PHẦN (kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg)
    List<IngredientEstimate> ingredients;
}
