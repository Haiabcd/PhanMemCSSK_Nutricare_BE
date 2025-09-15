package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.MealType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
    @Column(updatable = false, nullable = false, unique = true, name = "id")
    UUID id;
    @ManyToOne(fetch = FetchType.LAZY)
    MealPlanDay day;
    @Enumerated(EnumType.STRING)
    MealType mealType;
    @ManyToOne(fetch = FetchType.LAZY)
    Food food;
    @Column(name = "portion")
    BigDecimal portion;
    @Column(name = "rank")
    Integer rank;
    @Column(name = "note")
    String note;
    @Embedded
    Nutrition nutrition;
    @Column(name = "created_at")
    Instant createdAt;
}
