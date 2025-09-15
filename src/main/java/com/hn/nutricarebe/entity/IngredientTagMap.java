package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.IngredientTagCode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
public class IngredientTagMap {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "ingredient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ingredient_tags_ing")
    )
    Ingredient ingredient;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 50)
    IngredientTagCode tagCode;
}
