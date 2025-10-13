package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.FoodTag;
import com.hn.nutricarebe.enums.MealSlot;
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

    @Column(name = "name", nullable = false, unique = true)
    @NotBlank(message = "Tên không được để trống")
    String name;

    @Column(name = "description",columnDefinition = "text")
    String description;

    @Column(name = "image_key", unique = true)
    String imageKey;

    @Column(name = "default_servings", nullable = false)
    Integer defaultServing;

    @Column(name = "serving_name", nullable = false)
    String servingName;

    @Column(name = "serving_gram", precision = 10, scale = 2)
    BigDecimal servingGram;

    @Column(name = "cook_minutes")
    Integer cookMinutes;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "kcal", column = @Column(name = "kcal", precision = 10, scale = 2)),
            @AttributeOverride(name = "proteinG",  column = @Column(name = "proteinG", precision = 10, scale = 2)),
            @AttributeOverride(name = "carbG",    column = @Column(name = "carbG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fatG",      column = @Column(name = "fatG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fiberG",  column = @Column(name = "fiberG", precision = 10, scale = 2)),
            @AttributeOverride(name = "sodiumMg",    column = @Column(name = "sodiumMg", precision = 10, scale = 2)),
            @AttributeOverride(name = "sugarMg",      column = @Column(name = "sugarMg", precision = 10, scale = 2))
    })
    Nutrition nutrition;

    @Column(name = "is_ingredient", nullable = false)
    boolean isIngredient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by", nullable = false,
            foreignKey = @ForeignKey(name = "fk_foods_created_by_users")
    )
    User createdBy;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "food_meal_slots",
            joinColumns = @JoinColumn(
                    name = "food_id",
                    foreignKey = @ForeignKey(name = "fk_food_meal_slots_foods")
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "meal_slot", nullable = false)
    @Builder.Default
    Set<MealSlot> mealSlots = new HashSet<>();


    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "food_tags",
            joinColumns = @JoinColumn(
                    name = "food_id",
                    foreignKey = @ForeignKey(name = "fk_food_tags_foods")
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag", nullable = false, length = 50)
    @Builder.Default
    Set<FoodTag> tags = new HashSet<>();


    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<RecipeIngredient> ingredients = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<SavedFood> savedByUsers = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<PlanLog> loggedByUsers = new HashSet<>();

    @OneToMany(mappedBy = "food", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<MealPlanItem> inMealPlanItems = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
