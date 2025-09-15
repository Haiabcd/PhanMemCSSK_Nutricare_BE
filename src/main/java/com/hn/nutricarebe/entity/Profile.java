package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.Gender;
import com.hn.nutricarebe.enums.GoalType;
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
@Table(name = "profiles")
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, unique = true, name = "id")
    UUID id;
    @OneToOne(fetch = FetchType.LAZY)
    User user;
    @Column(length = 300, name = "height_cm")
    Integer heightCm;
    @Column(length = 150, name = "weight_kg")
    BigDecimal weightKg;
    @Enumerated(EnumType.STRING)
    Gender gender;
    @Column(name = "birth_year")
    Integer birthYear;
    @Enumerated(EnumType.STRING)
    GoalType goal;
    @Enumerated(EnumType.STRING)
    ActivityLevel activityLevel;
    @Column(name = "name")
    String name;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "updated_at")
    Instant updatedAt;
}
