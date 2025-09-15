package com.hn.nutricarebe.entity;

import jakarta.persistence.*;
import jdk.jfr.Name;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "conditions")
public class Condition {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, unique = true, name = "id")
    UUID id;
    @Column(nullable = false, unique = true, name = "name")
    String name;
    @CreationTimestamp
    @Column(name = "created_at")
    Instant createdAt;
}
