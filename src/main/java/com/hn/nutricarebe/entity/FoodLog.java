package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.enums.MealType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZonedDateTime;
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
    @Column(updatable = false, nullable = false, unique = true, name = "id")
    UUID id;

    User user;
    @Column(name = "eaten_at")
    ZonedDateTime eatenAt;
    @Enumerated(EnumType.STRING)
    MealType mealType;
    @ManyToOne(fetch = FetchType.LAZY)
    Food food;
    @Embedded
    Nutrition snapshot;
    Integer quantity;
    Integer gramOverride;
    String photoUrl;
    @Enumerated(EnumType.STRING)
    LogSource source;
    Instant createdAt;
}
