package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "users")
@ToString
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
     UUID id;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "role")
    Role role;

    @Column(name = "email", unique = true)
    String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider")
    Provider provider;

    @Column(name = "provider_user_id", unique = true)
    String providerUserId;

    @Column(name = "device_id")
    String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    UserStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;


    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Profile profile;


    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<WaterLog> waterLogs = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<MealPlanDay>  mealPlanDays = new HashSet<>();;

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    Set<UserCondition> userConditions = new HashSet<>();

    @OneToMany(mappedBy = "createdBy",fetch = FetchType.LAZY)
    Set<Food> foods = new HashSet<>();

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    Set<PlanLog> foodLogs = new HashSet<>();

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    Set<UserAllergy> userAllergies = new HashSet<>();

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    Set<SavedFood> savedFoods = new HashSet<>();
}
