package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.MealSlot;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter @Setter
@AllArgsConstructor @NoArgsConstructor @Builder
@Entity
@Table(
        name = "plan_logs",
        indexes = {
                @Index(name = "idx_dfl_user_date", columnList = "user_id,date"),
                @Index(name = "idx_dfl_user_date_slot", columnList = "user_id,date,meal_slot"),
        }
)
public class PlanLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_foodlogs_users"))
    User user;


    @Column(name = "date", nullable = false)
    LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 20)
    MealSlot mealSlot;


    // Bắt buộc có món (kể cả món user tự nhập -> đã lưu thành Food)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "food_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_foodlogs_foods"))
    Food food;

    // true nếu tick theo plan; false nếu user chọn món khác (nhưng vẫn là Food)
    @Builder.Default
    @Column(name = "is_from_plan", nullable = false)
    boolean isFromPlan = false;

    // Link về item trong plan (nếu isFromPlan=true)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_item_id",
            foreignKey = @ForeignKey(name = "fk_foodlogs_plan_item"))
    MealPlanItem planItem;

    // Khẩu phần đã ăn (1.0, 0.5, 1.5…)
    @Column(name = "portion", precision = 10, scale = 2)
    BigDecimal portion;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "kcal",     column = @Column(name = "actual_kcal",     precision = 10, scale = 2)),
            @AttributeOverride(name = "proteinG", column = @Column(name = "actual_proteinG", precision = 10, scale = 2)),
            @AttributeOverride(name = "carbG",    column = @Column(name = "actual_carbG",    precision = 10, scale = 2)),
            @AttributeOverride(name = "fatG",     column = @Column(name = "actual_fatG",     precision = 10, scale = 2)),
            @AttributeOverride(name = "fiberG",   column = @Column(name = "actual_fiberG",   precision = 10, scale = 2)),
            @AttributeOverride(name = "sodiumMg", column = @Column(name = "actual_sodiumMg", precision = 10, scale = 2)),
            @AttributeOverride(name = "sugarMg",  column = @Column(name = "actual_sugarMg",  precision = 10, scale = 2))
    })
    Nutrition actualNutrition;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
