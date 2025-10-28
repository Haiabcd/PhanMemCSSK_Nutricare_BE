package com.hn.nutricarebe.dto.ai;

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
public class DailyTargetsOverrides {
    Gender gender;                 // ví dụ: FEMALE
    GoalType goal;                 // LOSE/GAIN/MAINTAIN
    ActivityLevel activityLevel;   // đổi mức vận động
    Integer heightCm;              // đổi chiều cao
    Integer weightKg;              // đổi cân nặng
    Integer birthYear;             // để tính tuổi khác
    Integer targetWeightDeltaKg;   // ví dụ -5 (giảm 5kg)
    Integer targetDurationWeeks;   // ví dụ 10 tuần
}
