package com.hn.nutricarebe.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NutritionResponse {
    Integer kcal;
    BigDecimal proteinG;
    BigDecimal carbG;
    BigDecimal fatG;
    BigDecimal fiberG;
    Integer sodiumMg;
    BigDecimal sugarMg;
}
