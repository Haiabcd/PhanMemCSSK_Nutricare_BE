package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "water_logs")
public class WaterLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Column(name = "logged_at")
    Instant loggedAt;

    @Column(name = "ml")
    @PositiveOrZero(message = "Lượng nước phải là số không âm")
    @Builder.Default
    Integer ml = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    Instant createdAt;
}
