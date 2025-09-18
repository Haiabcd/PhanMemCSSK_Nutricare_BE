package com.hn.nutricarebe.entity;


import com.hn.nutricarebe.enums.Unit;
import jakarta.persistence.*;
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
@Table(name = "ingredients")
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @Column(name = "name", nullable = false, unique = true)
    String name;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "kcal", column = @Column(name = "per100_kcal")),
            @AttributeOverride(name = "proteinG",  column = @Column(name = "per100_proteinG", precision = 10, scale = 2)),
            @AttributeOverride(name = "carbG",    column = @Column(name = "per100_carbG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fatG",      column = @Column(name = "per100_fatG", precision = 10, scale = 2)),
            @AttributeOverride(name = "fiberG",  column = @Column(name = "per100_fiberG", precision = 10, scale = 2)),
            @AttributeOverride(name = "sodiumMg",    column = @Column(name = "per100_sodiumMg")),
            @AttributeOverride(name = "sugarMg",      column = @Column(name = "per100_sugarMg", precision = 10, scale = 2))
    })
    Nutrition per100;

    @Column(name = "image_url")
    String imageUrl;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "ingredient_aliases",
            joinColumns = @JoinColumn(name = "ingredient_id", foreignKey = @ForeignKey(name = "fk_aliases_ingredient"))
    )
    @Column(name = "alias", length = 255, nullable = false)
    Set<String> aliases;

    @Column(name = "serving_name")
    String servingName;

    @Column(name = "serving_size_gram", precision = 10, scale = 2)
    BigDecimal servingSizeGram;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit", nullable = false)
    Unit unit;

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<IngredientTagMap> tags = new HashSet<>();

    @OneToMany(mappedBy = "ingredient", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<RecipeIngredient> recipeIngredients = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
