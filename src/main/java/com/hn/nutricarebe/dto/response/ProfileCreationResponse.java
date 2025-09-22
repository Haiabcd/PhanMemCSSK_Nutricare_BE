package com.hn.nutricarebe.dto.response;

import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.Gender;
import com.hn.nutricarebe.enums.GoalType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

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
    Instant createdAt;
    Instant updatedAt;
}
