package com.hn.nutricarebe.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name="ai_summaries")
public class AiSummary {
    @Id
    @Column(name="user_id")
    UUID userId;
    @Column(nullable=false, columnDefinition="text")
    String summary;
    @Column(name="updated_at")
    @UpdateTimestamp
    Instant updatedAt;
}
