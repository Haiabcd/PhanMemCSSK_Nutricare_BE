package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
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
@Table(name = "allergies")
public class Allergy {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @Column(nullable = false, unique = true, name = "name")
    String name;

    @OneToMany(mappedBy = "allergy", fetch = FetchType.LAZY)
    Set<UserAllergy> userAllergies = new HashSet<>();

    @OneToMany(mappedBy = "allergy", fetch = FetchType.LAZY)
    Set<NutritionRule> nutritionRules = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    Instant createdAt;
}
