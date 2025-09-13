package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
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
@Table(name = "accounts")
public class Account {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, unique = true)
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
     String provider_user_id;
    @Column(name = "installation_id")
     String installation_id;
    @Column(name = "refresh_version")
     int refresh_version;
    @CreationTimestamp
     Instant created_at;
    @Column(name = "last_login_at")
     Instant last_seen_at;
}
