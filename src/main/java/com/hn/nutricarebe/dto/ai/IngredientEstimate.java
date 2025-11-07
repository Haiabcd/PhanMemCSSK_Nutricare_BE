package com.hn.nutricarebe.dto.ai;

import java.math.BigDecimal;

import com.hn.nutricarebe.enums.Unit;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientEstimate {
    String name;
    BigDecimal amount;
    Unit unit;
}
