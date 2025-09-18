package com.hn.nutricarebe.dto.request;


import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.hn.nutricarebe.enums.IngredientTag;
import com.hn.nutricarebe.enums.Unit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IngredientCreationRequest {
    @NotBlank(message = "Tên nguyên liệu không được để trống")
    String name;

    @Valid
    @NotNull(message = "Thông tin dinh dưỡng trên 100g là bắt buộc")
    NutritionRequest per100;

    String imageUrl;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    Set<@Size(max = 100, message = "Mỗi alias tối đa 100 ký tự") String> aliases = new HashSet<>();

    String servingName;

    @PositiveOrZero(message = "servingSizeGram phải là số dương hoặc bằng 0")
    @Digits(integer = 8, fraction = 2, message = "servingSizeGram tối đa 8 số nguyên và 2 số thập phân")
    BigDecimal servingSizeGram;

    @NotNull(message = "Đơn vị là bắt buộc")
    Unit unit;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    Set<IngredientTag> tags = new HashSet<>();
}
