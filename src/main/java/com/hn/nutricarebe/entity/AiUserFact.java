package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
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
@Table(name="ai_user_facts", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","key"}))
public class AiUserFact {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    Long id;
    @Column(name="user_id", nullable=false)
    UUID userId;
    @Column(name="key", nullable=false)
    String key;
    @Column(name="value", nullable=false, columnDefinition="jsonb")
    String value;
    @Column
    String source;
    @Column(name="updated_at")
    @UpdateTimestamp
    Instant updatedAt;
}
