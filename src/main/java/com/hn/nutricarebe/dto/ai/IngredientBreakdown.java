package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientBreakdown {
    String requestedName;
    UUID ingredientId;     // id Ingredient khớp DB (nếu có)
    String matchedName;    // tên trong DB
    String aliasMatched;   // alias khớp (nếu có)
    BigDecimal gram;       // gram đã quy đổi
    Nutrition per100;      // dinh dưỡng /100g từ DB
    Nutrition subtotal;    // per100 * (gram/100)
    Boolean missing;
    String imageUrl;
}
