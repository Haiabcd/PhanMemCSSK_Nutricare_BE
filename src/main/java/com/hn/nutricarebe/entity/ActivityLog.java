package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.ActivityType;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

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
    @Column(updatable = false, nullable = false, unique = true, name = "id")
    UUID id;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user", nullable = false,
            foreignKey = @ForeignKey(name = "account"))
    User user;
    @Column(name = "time")
    ZonedDateTime time;
    @Enumerated(EnumType.STRING)
    ActivityType type;
    @Column(name = "minutes")
    Integer minutes;
    @Column(name = "kcal_burned")
    Integer kcalBurned;
    Instant createdAt;
}
