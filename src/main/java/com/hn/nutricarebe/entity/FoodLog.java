package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.enums.MealSlot;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "food_logs")
public class FoodLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_foods_created_by_users")
    )

    User user;

    @Column(name = "eaten_at", nullable = false)
    Instant eatenAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false, length = 20)
    MealSlot mealSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)                                 // 1 món (Food) có thể xuất hiện ở nhiều log
    @JoinColumn(
            name = "food_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_food_logs_food")                             // FK → foods.id
    )
    Food food;

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
    Nutrition snapshot;

    @Column(name = "quantity")
    Integer quantity;

    @Column(name = "gram_override")
    Integer gramOverride;

    @Column(name = "photo_url", length = 1024)
    String photoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    LogSource source;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;
}
