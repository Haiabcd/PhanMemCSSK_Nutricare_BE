package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RecipeIngredientCreationRequest {
    @NotNull(message = "ingredientId là bắt buộc")
    UUID ingredientId;

    @NotNull(message = "quantity là bắt buộc")
    @Digits(integer = 8, fraction = 2)
    @Positive(message = "quantity phải > 0")
    BigDecimal quantity;
}
