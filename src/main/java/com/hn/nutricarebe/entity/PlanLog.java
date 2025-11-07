package com.hn.nutricarebe.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.enums.MealSlot;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "plan_logs",
        indexes = {
            @Index(name = "idx_dfl_user_date", columnList = "user_id,date"),
            @Index(name = "idx_dfl_user_date_slot", columnList = "user_id,date,meal_slot"),
            @Index(name = "idx_dfl_user_date_source", columnList = "user_id,date,source")
        })
public class PlanLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_foodlogs_users"))
    User user;

    @Column(name = "date", nullable = false)
    LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 20)
    MealSlot mealSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "food_id", foreignKey = @ForeignKey(name = "fk_foodlogs_foods"))
    Food food;

    @Column(name = "name_food")
    String nameFood;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    LogSource source;

    @Column(name = "portion", precision = 10, scale = 2)
    BigDecimal portion;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "kcal", column = @Column(name = "actual_kcal", precision = 10, scale = 2)),
        @AttributeOverride(name = "proteinG", column = @Column(name = "actual_proteinG", precision = 10, scale = 2)),
        @AttributeOverride(name = "carbG", column = @Column(name = "actual_carbG", precision = 10, scale = 2)),
        @AttributeOverride(name = "fatG", column = @Column(name = "actual_fatG", precision = 10, scale = 2)),
        @AttributeOverride(name = "fiberG", column = @Column(name = "actual_fiberG", precision = 10, scale = 2)),
        @AttributeOverride(name = "sodiumMg", column = @Column(name = "actual_sodiumMg", precision = 10, scale = 2)),
        @AttributeOverride(name = "sugarMg", column = @Column(name = "actual_sugarMg", precision = 10, scale = 2))
    })
    Nutrition actualNutrition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_item_id", foreignKey = @ForeignKey(name = "fk_foodlogs_plan_item"))
    MealPlanItem planItem;

    @OneToMany(mappedBy = "planLog", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<PlanLogIngredient> ingredients = new HashSet<>();

    @Column(name = "serving_size_gram", precision = 10, scale = 2) // 1 khẩu phần từ scan
    BigDecimal servingSizeGram;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
