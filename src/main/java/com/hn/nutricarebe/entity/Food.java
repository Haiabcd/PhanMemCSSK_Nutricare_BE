package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "foods")
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, unique = true, name = "id")
    UUID id;
    @Column(name = "name")
    String name;
    @Column(name = "description")
    String description;
    String imageUrl;
    @Column(name = "serving_name")
    String servingName;
    @Column(name = "serving_gram")
    BigDecimal servingGram;
    @Column(name = "cook_minutes")
    Integer cookMinutes;
    @Embedded
    Nutrition nutrition;
    @ManyToOne(fetch = FetchType.LAZY)
    User createdBy;
    @Column(name = "created_at")
    Instant createdAt;
    @Column(name = "updated_at")
    Instant updatedAt;
}
