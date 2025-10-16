package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.Gender;
import com.hn.nutricarebe.enums.GoalType;
import jakarta.persistence.*;
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
@Entity
@Table(name = "profiles")
@ToString(exclude = "user")
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Column(name = "height_cm", nullable = false)
    Integer heightCm;

    @Column(name = "weight_kg", nullable = false)
    Integer weightKg;

    @Column(name = "target_weight_delta_kg", nullable = false)
    Integer targetWeightDeltaKg;

    @Column(name = "target_duration_week", nullable = false)
    Integer targetDurationWeeks;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false)
    Gender gender;

    @Column(name = "birth_year", nullable = false)
    Integer birthYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal", nullable = false)
    GoalType goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level")
    ActivityLevel activityLevel;

    @Column(name = "name")
    String name;

    @Column(name = "avatar_url")
    String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
