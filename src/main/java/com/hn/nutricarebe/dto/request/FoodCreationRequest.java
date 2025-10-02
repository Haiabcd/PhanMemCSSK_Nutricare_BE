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

    @NotNull(message = "Số khẩu phần mặc định là bắt buộc")
    @Min(value = 1, message = "Số khẩu phần mặc định phải >= 1")
    @Builder.Default
    Integer defaultServing = 0 ;

    @NotBlank(message = "Tên khẩu phần không được để trống")
    String servingName;

    @Digits(integer=8, fraction=2)
    @PositiveOrZero(message ="Khối lượng phục vụ phải là số dương hoặc bằng 0")
    @Builder.Default
    BigDecimal servingGram = BigDecimal.ZERO;

    @Builder.Default
    @Min(value = 0, message = "Thời gian nấu phải >= 0")
    Integer cookMinutes = 0;

    @Valid
    @NotNull(message = "Thông tin dinh dưỡng là bắt buộc")
    NutritionRequest nutrition;

    @Builder.Default
    boolean isIngredient = false;

    @Builder.Default
    Set<MealSlot> mealSlots = new HashSet<>();

    @Builder.Default
    Set<FoodTag> tags= new HashSet<>();

    @NotNull(message = "Ảnh món ăn là bắt buộc")
    MultipartFile image;
}