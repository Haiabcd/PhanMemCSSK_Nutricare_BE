package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.*;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Embeddable
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionRule {

    UUID id;
    Condition condition;
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
    String targetCode;

    @Column(name = "threshold", precision = 12, scale = 2, nullable = false)
    BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    RuleSeverity severity;

    String message;

    @Column(name = "config_json", length = 16, nullable = false)
    String configJson;
    Instant createAt;
    Instant updateAt;
}
