package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RecipeIngredientResponse {
    UUID ingredientId;
    String name;
    String unit;
    String imageUrl;
    BigDecimal quantity;
}
