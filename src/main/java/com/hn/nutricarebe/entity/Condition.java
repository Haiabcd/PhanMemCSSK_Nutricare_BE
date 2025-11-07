package com.hn.nutricarebe.entity;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;

import lombok.*;
import lombok.experimental.FieldDefaults;

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
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @Column(nullable = false, unique = true, name = "name")
    String name;

    @OneToMany(mappedBy = "condition", fetch = FetchType.LAZY)
    Set<UserCondition> userConditions = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at")
    Instant createdAt;
}
