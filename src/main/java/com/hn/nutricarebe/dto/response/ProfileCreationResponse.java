package com.hn.nutricarebe.dto.response;

import java.time.Instant;
import java.util.UUID;

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
public class ProfileCreationResponse {
    UUID id;
    Integer heightCm;
    Integer weightKg;
    Gender gender;
    Integer birthYear;
    GoalType goal;
    ActivityLevel activityLevel;
    String name;
    String avatarUrl;
    Integer targetWeightDeltaKg;
    Integer targetDurationWeeks;
    boolean goalReached;
    Instant createdAt;
    Instant updatedAt;
}
