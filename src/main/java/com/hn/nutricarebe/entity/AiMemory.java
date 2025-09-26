package com.hn.nutricarebe.entity;


import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name="ai_memory")
public class AiMemory {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    Long id;
    @Column(name="user_id", nullable=false)
    String userId;
    @Column(nullable=false)
    String role;     // user | assistant
    @Column(nullable=false, columnDefinition="text")
    String content;
    @Column(columnDefinition="jsonb")
    String meta;
    @Column(name="created_at")
    @CreationTimestamp
    Instant createdAt;
}
