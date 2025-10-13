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

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "water_logs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_waterlog_user_date", columnNames = {"user_id", "log_date"})
        },
        indexes = {
                @Index(name = "idx_waterlog_user_date", columnList = "user_id,log_date"),
                @Index(name = "idx_waterlog_date", columnList = "log_date")
        }
)
public class WaterLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    // Một user có nhiều water_log theo ngày
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_waterlog_user"))
    User user;

    // Ngày được set theo timezone ứng dụng (LocalDate business-day)
    @Column(name = "log_date", nullable = false)
    @NotNull
    LocalDate date;

    // Tổng ml đã uống trong ngày (aggregate). Để NOT NULL + mặc định 0
    @Column(name = "ml", nullable = false)
    @PositiveOrZero(message = "Lượng nước phải là số không âm")
    @Builder.Default
    Integer ml = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    public void addMl(int delta) {
        if (delta < 0) return;
        this.ml = (this.ml == null ? 0 : this.ml) + delta;
    }
}
