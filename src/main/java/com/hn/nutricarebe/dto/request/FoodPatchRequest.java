package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.entity.Tag;
import com.hn.nutricarebe.enums.MealSlot;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.util.Set;


@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FoodPatchRequest {
    @Size(min = 1, message = "Tên không được rỗng nếu gửi")
    String name;

    String description;

    @Min(value = 1, message = "Số khẩu phần mặc định phải >= 1")
    Integer defaultServing;

    @Size(min = 1, message = "Tên khẩu phần không được rỗng nếu gửi")
    String servingName;

    @Digits(integer = 8, fraction = 2)
    @PositiveOrZero(message = "Khối lượng phục vụ phải >= 0")
    BigDecimal servingGram;

    @Min(value = 0, message = "Thời gian nấu phải >= 0")
    Integer cookMinutes;

    @Valid
    NutritionRequest nutrition;

    Boolean isIngredient;

    Set<MealSlot> mealSlots;
    Set<Tag> tags;

    MultipartFile image;
}
