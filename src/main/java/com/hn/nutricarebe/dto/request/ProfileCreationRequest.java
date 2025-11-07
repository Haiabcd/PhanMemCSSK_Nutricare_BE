package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import org.hibernate.validator.constraints.Range;

import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.Gender;
import com.hn.nutricarebe.enums.GoalType;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileCreationRequest {
    @NotNull(message = "Chiều cao là bắt buộc")
    @Range(min = 80, max = 250, message = "Chiều cao phải từ 80cm đến 250cm")
    Integer heightCm;

    @NotNull(message = "Cân nặng là bắt buộc")
    @Range(min = 30, max = 200, message = "Cân nặng phải từ 30kg đến 200kg")
    Integer weightKg;

    @NotNull(message = "Cân nặng mục tiêu là bắt buộc")
    @Builder.Default
    Integer targetWeightDeltaKg = 0;

    @NotNull(message = "Số tuần mục tiêu là bắt buộc")
    @PositiveOrZero(message = "Số tuần mục tiêu phải là số nguyên dương hoặc bằng 0")
    @Builder.Default
    Integer targetDurationWeeks = 0;

    @NotNull(message = "Giới tính là bắt buộc")
    Gender gender;

    @NotNull(message = "Năm sinh là bắt buộc")
    Integer birthYear;

    @NotNull(message = "Mục tiêu là bắt buộc")
    GoalType goal;

    @NotNull(message = "Mức độ hoạt động là bắt buộc")
    ActivityLevel activityLevel;

    @NotBlank(message = "Tên là bắt buộc")
    String name;
}
