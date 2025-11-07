package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PlanLogIngredientResponse {
    UUID id;
    IngredientResponse ingredient;
    BigDecimal quantity;
}
