package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.FoodTagCode;
import jakarta.persistence.Embeddable;
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

public class FoodTagMap {
    UUID id;
    Food food;
    FoodTagCode tagCode;
}
