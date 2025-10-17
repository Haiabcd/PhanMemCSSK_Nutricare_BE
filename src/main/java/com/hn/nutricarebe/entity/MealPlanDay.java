package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "meal_plan_days",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_id", "date"})
        }
)
public class MealPlanDay {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    @Column(name = "date", nullable = false)
    LocalDate date;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "kcal", column = @Column(name = "target_kcal")),
            @AttributeOverride(name = "proteinG",  column = @Column(name = "target_proteinG", precision = 10, scale = 2)),
            @AttributeOverride(name = "carbG",    column = @Column(name = "target_carbG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fatG",      column = @Column(name = "target_fatG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fiberG",  column = @Column(name = "target_fiberG", precision = 10, scale = 2)),
            @AttributeOverride(name = "sodiumMg",    column = @Column(name = "target_sodiumMg")),
            @AttributeOverride(name = "sugarMg",      column = @Column(name = "target_sugarMg", precision = 10, scale = 2))
    })
    Nutrition targetNutrition;

    @Column(name = "water_target_ml")
    Integer waterTargetMl;

    @OneToMany(mappedBy = "day", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<MealPlanItem> items = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
