package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
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

    @Column(name = "device_id", unique = true)
    String deviceId;   //duy nhat

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    UserStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    Instant updatedAt;


    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    Profile profile;


    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    WaterLog waterLog;

    @OneToOne(mappedBy = "user", fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    ActivityLog activityLog;

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    Set<MealPlanDay>  mealPlanDay = new HashSet<>();;

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    Set<UserCondition> userConditions = new HashSet<>();

    @OneToMany(mappedBy = "createdBy",fetch = FetchType.LAZY)
    Set<Food> foods = new HashSet<>();

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    Set<FoodLog> foodLogs = new HashSet<>();

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY)
    Set<UserAllergy> userAllergies = new HashSet<>();

    @OneToMany(mappedBy = "user",fetch = FetchType.LAZY,
            cascade = CascadeType.ALL, orphanRemoval = true)
    Set<SavedFood> savedFoods = new HashSet<>();


}
