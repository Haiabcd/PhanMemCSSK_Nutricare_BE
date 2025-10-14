package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "water_logs",
        indexes = {
                @Index(name = "idx_waterlog_user_date", columnList = "user_id,log_date"),
                @Index(name = "idx_waterlog_drinked_at", columnList = "drinked_at")
        }
)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WaterLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_waterlog_user"))
    User user;

    // Thời điểm uống thực tế (UTC)
    @Column(name = "drinked_at", nullable = false)
    Instant drinkedAt;

    // Ngày nghiệp vụ (theo timezone app) để nhóm theo ngày
    @Column(name = "log_date", nullable = false)
    LocalDate date;

    // Số ml CỦA LẦN UỐNG NÀY (dương: cộng, âm: trừ/undo)
    @Column(name = "amount_ml", nullable = false)
    Integer amountMl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
