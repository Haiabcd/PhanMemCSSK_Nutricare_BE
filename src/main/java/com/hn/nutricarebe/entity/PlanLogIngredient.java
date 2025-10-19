package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.util.UUID;


@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "plan_log_ingredients",
        indexes = {
                @Index(name = "idx_pli_planlog", columnList = "plan_log_id"),
                @Index(name = "idx_pli_ingredient", columnList = "ingredient_id")
        }
)
public class PlanLogIngredient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_log_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_pli_planlog"))
    PlanLog planLog;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id",
            foreignKey = @ForeignKey(name = "fk_pli_ingredient"))
    Ingredient ingredient;

    // Số gram dùng thực tế
    @Column(name = "quantity_gram", precision = 10, scale = 2, nullable = false)
    BigDecimal quantity;

}
