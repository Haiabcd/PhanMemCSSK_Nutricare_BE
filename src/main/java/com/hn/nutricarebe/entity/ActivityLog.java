package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.ActivityType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
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
@Table(name = "activity_logs")
public class ActivityLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;


    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    User user;

    @Column(name = "logged_at")
    ZonedDateTime loggedAt;

    @Enumerated(EnumType.STRING)
    @NotNull(message = "Hoat động là bắt buộc")
    ActivityType type;

    @Column(name = "minutes")
    Integer minutes;

    @Column(name = "kcal_burned")
    Integer kcalBurned;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    Instant createdAt;
}
