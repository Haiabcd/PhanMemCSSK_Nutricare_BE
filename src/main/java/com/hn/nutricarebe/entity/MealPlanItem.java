package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.MealType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "meal_plan_items")
public class MealPlanItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "day_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mpi_day")
    )
    MealPlanDay day;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    MealType mealType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "food_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mpi_food")
    )
    Food food;

    @Column(name = "portion", precision = 10, scale = 2)
    BigDecimal portion;

    @Column(name = "rank", nullable = false)
    Integer rank;

    @Column(name = "note", columnDefinition = "text")
    String note;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "kcal", column = @Column(name = "snap_kcal")),
            @AttributeOverride(name = "proteinG",  column = @Column(name = "snap_proteinG", precision = 10, scale = 2)),
            @AttributeOverride(name = "carbG",    column = @Column(name = "snap_carbG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fatG",      column = @Column(name = "snap_fatG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fiberG",  column = @Column(name = "snap_fiberG", precision = 10, scale = 2)),
            @AttributeOverride(name = "sodiumMg",    column = @Column(name = "snap_sodiumMg")),
            @AttributeOverride(name = "sugarMg",      column = @Column(name = "snap_sugarMg", precision = 10, scale = 2))
    })
    Nutrition nutrition;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
