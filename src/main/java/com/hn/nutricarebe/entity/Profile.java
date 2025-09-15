package com.hn.nutricarebe.entity;

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
@Entity
@Table(name = "profiles")
public class Profile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Column(length = 300, name = "height_cm", nullable = false)
    @NotNull(message = "Chiều cao là bắt buộc")
    Integer heightCm;

    @Column(length = 150, name = "weight_kg", nullable = false)
    @NotNull(message = "Cân nặng là bắt buộc")
    Integer weightKg;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Giới tính là bắt buộc")
    @Column(name = "gender", nullable = false)
    Gender gender;

    @Column(name = "birth_year", nullable = false)
    Integer birthYear;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Mục tiêu là bắt buộc")
    @Column(name = "goal", nullable = false)
    GoalType goal;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level")
    ActivityLevel activityLevel;

    @NotBlank(message = "Tên là bắt buộc")
    @Column(name = "name")
    String name;

    @CreationTimestamp
    @Column(name = "created_at")
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;
}
