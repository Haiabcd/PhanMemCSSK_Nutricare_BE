package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.*;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_id", foreignKey = @ForeignKey(name = "fk_nutrition_rule_condition"))
    Condition condition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allergy_id", foreignKey = @ForeignKey(name = "fk_nutrition_rule_allergy"))
    Allergy allergy;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    RuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    RuleScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    TargetType targetType;

    @Column(name = "target_code", nullable = false)
    String targetCode;

    @Column(name = "threshold", precision = 12, scale = 2, nullable = false)
    BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    RuleSeverity severity;

    @Column(name = "message", nullable = false)
    String message;

    @Column(name = "config_json", length = 16, nullable = false)
    String configJson;

    @CreationTimestamp
    @Column(name = "create_at", updatable = false, nullable = false)
    Instant createAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    Instant updateAt;
}
