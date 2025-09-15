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
    @Column(name = "kcal", nullable = false)
    Integer kcal;
    @Column(name = "protein_g", precision = 12, scale = 2, nullable = false)
    BigDecimal proteinG;
    @Column(name = "carb_g", precision = 12, scale = 2, nullable = false)
    BigDecimal carbG;
    @Column(name = "fat_g", precision = 12, scale = 2, nullable = false)
    BigDecimal fatG;
    @Column(name = "fiber_g", precision = 12, scale = 2, nullable = false)
    BigDecimal fiberG;
    @Column(name = "sodium_mg", nullable = false)
    Integer sodiumMg;
    @Column(name = "sugar_mg")
    BigDecimal sugarMg;
}
