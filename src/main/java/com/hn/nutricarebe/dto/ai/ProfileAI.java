package com.hn.nutricarebe.dto.ai;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;


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
