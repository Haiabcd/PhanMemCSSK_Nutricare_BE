package com.hn.nutricarebe.dto.ai;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileAI {
    Integer heightCm;
    Integer weightKg;
    String gender;
    Integer age;
    String goal;
    String activityLevel;
    List<String> conditions;
    List<String> allergies;
    List<String> nutritionRules;
}
