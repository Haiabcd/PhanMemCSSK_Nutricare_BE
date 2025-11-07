package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NutritionResponse {
    BigDecimal kcal;
    BigDecimal proteinG;
    BigDecimal carbG;
    BigDecimal fatG;
    BigDecimal fiberG;
    BigDecimal sodiumMg;
    BigDecimal sugarMg;
}
