package com.hn.nutricarebe.dto.request;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NutritionRequest {
    @PositiveOrZero(message = "kcal phải >= 0")
    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    Integer kcal = 0;

    @PositiveOrZero(message = "proteinG phải >= 0")
    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    @Digits(integer = 8, fraction = 2, message = "proteinG tối đa 8 số nguyên và 2 số thập phân")
    BigDecimal proteinG = BigDecimal.ZERO;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    @PositiveOrZero(message = "carbG phải >= 0")
    @Digits(integer = 8, fraction = 2, message = "carbG tối đa 8 số nguyên và 2 số thập phân")
    BigDecimal carbG = BigDecimal.ZERO;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    @PositiveOrZero(message = "fatG phải >= 0")
    @Digits(integer = 8, fraction = 2, message = "fatG tối đa 8 số nguyên và 2 số thập phân")
    BigDecimal fatG = BigDecimal.ZERO;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    @PositiveOrZero(message = "fiberG phải >= 0")
    @Digits(integer = 8, fraction = 2,message = "fiberG tối đa 8 số nguyên và 2 số thập phân")
    BigDecimal fiberG= BigDecimal.ZERO;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    @PositiveOrZero(message = "sodiumMg phải >= 0")
    Integer sodiumMg= 0;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    @PositiveOrZero(message = "sugarMg phải >= 0")
    @Digits(integer = 8, fraction = 2, message = "sugarMg tối đa 8 số nguyên và 2 số thập phân")
    BigDecimal sugarMg= BigDecimal.ZERO;
}
