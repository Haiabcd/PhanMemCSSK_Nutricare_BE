package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.FoodTagCode;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)

public class FoodTagMap {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "food_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_food_tags_food")
    )
    Food food;

    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 50)
    FoodTagCode tagCode;
}
