package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.enums.FoodTag;
import com.hn.nutricarebe.enums.MealSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FoodCreationRequest {
    @NotBlank(message = "Tên không được để trống")
    String name;
    String description;
    @NotBlank(message = "Tên khẩu phần không được để trống")
    String servingName;

    @Digits(integer=8, fraction=2)
    @PositiveOrZero(message ="Khối lượng phục vụ phải là số dương hoặc bằng 0")
    BigDecimal servingGram;
    @Builder.Default
    @Min(value = 0, message = "Thời gian nấu phải >= 0")
    Integer cookMinutes = 0;
    @Valid
    @NotNull(message = "Thông tin dinh dưỡng là bắt buộc")
    NutritionRequest nutrition;
    @Builder.Default
    boolean isIngredient = false;
    UUID createdBy;
    @Builder.Default
    Set<MealSlot> mealSlots = new HashSet<>();
    @Builder.Default
    Set<FoodTag> tags= new HashSet<>();

    MultipartFile image;
}
