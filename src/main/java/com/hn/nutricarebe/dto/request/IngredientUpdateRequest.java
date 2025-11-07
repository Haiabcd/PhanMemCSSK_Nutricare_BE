package com.hn.nutricarebe.dto.request;

import java.util.HashSet;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.hn.nutricarebe.enums.Unit;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IngredientUpdateRequest {
    @NotBlank(message = "Tên nguyên liệu không được để trống")
    String name;

    @Valid
    @NotNull(message = "Thông tin dinh dưỡng trên 100g là bắt buộc")
    NutritionRequest per100;

    MultipartFile image;

    @Builder.Default
    @JsonSetter(nulls = Nulls.SKIP)
    Set<@Size(max = 100, message = "Mỗi alias tối đa 100 ký tự") String> aliases = new HashSet<>();

    @NotNull(message = "Đơn vị là bắt buộc")
    @Builder.Default
    Unit unit = Unit.G;
}
