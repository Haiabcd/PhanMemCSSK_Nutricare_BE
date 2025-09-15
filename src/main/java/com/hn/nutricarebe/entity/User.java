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

import java.time.Instant;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "accounts")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, unique = true, name = "id")
     UUID id;
    @NotNull(message = "Role là bắt buộc")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "role")
    Role role;
    @Column(name = "email", unique = true)
    @Email(message = "Email không hợp lệ")
     String email;
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Provider là bắt buộc")
    @Column(name = "provider")
    Provider provider;
    @Column(name = "provider_user_id")
     String providerUserId;
    @Column(name = "device_id")
     String deviceId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    UserStatus status;
    @CreationTimestamp
     Instant createdAt;
    @Column(name = "last_seen_at")
     Instant lastSeenAt;
}
