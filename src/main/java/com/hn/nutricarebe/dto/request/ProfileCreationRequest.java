package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.Gender;
import com.hn.nutricarebe.enums.GoalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.validator.constraints.Range;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileCreationRequest {

    UUID userId;

    @NotNull(message = "Chiều cao là bắt buộc")
    @Range(min = 100, max = 250, message = "Chiều cao phải từ 100cm đến 250cm")
    Integer heightCm;

    @NotNull(message = "Cân nặng là bắt buộc")
    @Range(min = 30, max = 100, message = "Cân nặng phải từ 30kg đến 100kg")
    Integer weightKg;

    @NotNull(message = "Giới tính là bắt buộc")
    Gender gender;

    @NotNull(message = "Năm sinh là bắt buộc")
    @Range(min = 1900, max = 2024, message = "Năm sinh không hợp lệ")
    Integer birthYear;

    @NotNull(message = "Mục tiêu là bắt buộc")
    GoalType goal;

    @NotNull(message = "Mức độ hoạt động là bắt buộc")
    ActivityLevel activityLevel;

    @NotBlank(message = "Tên là bắt buộc")
    String name;

}
