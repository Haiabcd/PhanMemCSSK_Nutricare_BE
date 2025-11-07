package com.hn.nutricarebe.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "refresh_tokens",
        indexes = {@Index(name = "idx_family", columnList = "familyId")})
public class RefreshToken {
    @Id
    String jti; // jti từ JWT

    @Column(nullable = false)
    UUID userId;

    @Column(nullable = false, length = 64)
    String familyId; // để chống reuse theo "token family"

    @Column(nullable = false)
    Instant expiresAt;

    @Column(nullable = false)
    boolean rotated; // đổi refresh token

    @Column(nullable = false)
    boolean revoked; // bị vô hiệu (thu hồi toàn bộ family)

    String replacedByJti; // jti mới nếu đã rotation

    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    Instant createdAt;
}
