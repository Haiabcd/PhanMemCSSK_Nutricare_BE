package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "water_logs",
        indexes = {
                @Index(name = "idx_waterlog_user_date", columnList = "user_id,log_date"),
                @Index(name = "idx_waterlog_user_drankat", columnList = "user_id,drank_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class WaterLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_waterlog_user"))
    User user;

    // Ngày nghiệp vụ theo TZ app, derive từ drankAt
    @Column(name = "log_date", nullable = false)
    LocalDate date;

    // Thời điểm uống thực tế (UTC)
    @Column(name = "drank_at", nullable = false)
    Instant drankAt;

    // Lượng nước ở LẦN uống này
    @Column(name = "amount_ml", nullable = false)
    Integer amountMl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;
}
