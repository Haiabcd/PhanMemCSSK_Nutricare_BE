package com.hn.nutricarebe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Nutrition {
    BigDecimal kcal;
    BigDecimal proteinG;
    BigDecimal carbG;
    BigDecimal fatG;
    BigDecimal fiberG;
    BigDecimal sodiumMg;
    BigDecimal sugarMg;
}
