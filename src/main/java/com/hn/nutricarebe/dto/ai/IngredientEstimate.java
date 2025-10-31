package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.enums.Unit;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

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
