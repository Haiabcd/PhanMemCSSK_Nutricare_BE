package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.IngredientTagCode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class IngredientTagMap {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    UUID id;
    @ManyToOne
    @JoinColumn(name = "ingredient", referencedColumnName = "id")
    Ingredient ingredient;
    @Enumerated(EnumType.STRING)
    IngredientTagCode tagCode;
}
