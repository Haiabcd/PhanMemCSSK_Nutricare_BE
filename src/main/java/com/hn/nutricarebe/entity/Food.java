package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
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
@Table(name = "foods")
public class Food {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @Column(name = "name", nullable = false, length = 255, unique = true)
    @NotBlank(message = "Tên không được để trống")
    String name;

    @Column(name = "description")
    String description;

    @Column(name = "image_url", length = 1024)
    String imageUrl;

    @Column(name = "serving_name", length = 100)
    String servingName;

    @Column(name = "serving_gram", precision = 10, scale = 2)
    BigDecimal servingGram;

    @Column(name = "cook_minutes")
    Integer cookMinutes;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "kcal", column = @Column(name = "kcal")),
            @AttributeOverride(name = "proteinG",  column = @Column(name = "proteinG", precision = 10, scale = 2)),
            @AttributeOverride(name = "carbG",    column = @Column(name = "carbG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fatG",      column = @Column(name = "fatG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fiberG",  column = @Column(name = "fiberG", precision = 10, scale = 2)),
            @AttributeOverride(name = "sodiumMg",    column = @Column(name = "sodiumMg")),
            @AttributeOverride(name = "sugarMg",      column = @Column(name = "sugarMg", precision = 10, scale = 2))
    })
    Nutrition nutrition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_foods_created_by_users")
    )
    User createdBy;

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<FoodTagMap> tags = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<RecipeIngredient> ingredients = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<SavedFood> savedByUsers = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<FoodLog> loggedByUsers = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<MealPlanItem> inMealPlanItems = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
