package com.hn.nutricarebe.entity;

import com.hn.nutricarebe.enums.*;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "nutrition_rule",
        indexes = {
                @Index(name = "idx_rule_condition", columnList = "condition_id"),
                @Index(name = "idx_rule_target", columnList = "target_type,target_code"),
                @Index(name = "idx_rule_scope", columnList = "scope"),
                @Index(name = "idx_rule_active", columnList = "active")
        }
)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, name = "id")
    UUID id;

    // Liên kết bệnh lý (gout, T2D, CKD...)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "condition_id", foreignKey = @ForeignKey(name = "fk_nutrition_rule_condition"))
    Condition condition;

    // Dị ứng (nếu có)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allergy_id", foreignKey = @ForeignKey(name = "fk_nutrition_rule_allergy"))
    Allergy allergy;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    RuleType ruleType;

    // Phạm vi áp dụng: ITEM, MEAL, DAY
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    RuleScope scope;

    // Đối tượng: NUTRIENT, FOOD_TAG
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    TargetType targetType;

    // Mã đích: ví dụ "NA" (natri) nếu NUTRIENT; "HIGH_PURINE" nếu FOOD_TAG
    @Column(name = "target_code", nullable = false, length = 128)
    String targetCode;

    // ===== So sánh & ngưỡng =====
    @Enumerated(EnumType.STRING)
    @Column(name = "comparator", nullable = false, length = 16)
    Comparator comparator;

    @Column(name = "threshold_min", precision = 12, scale = 4)
    BigDecimal thresholdMin;

    @Column(name = "threshold_max", precision = 12, scale = 4)
    BigDecimal thresholdMax;

    // Tính theo kg thể trọng (ví dụ: PROTEIN ≥ 1.0 g/kg/day)
    @Builder.Default
    @Column(name = "per_kg", nullable = false)
    Boolean perKg = Boolean.FALSE;

    @Column(name = "frequency_per_scope")
    Integer frequencyPerScope;

    @Builder.Default
    @Column(name = "active", nullable = false)
    Boolean active = Boolean.TRUE;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_sex", length = 16)
    Gender applicableSex;

    @Column(name = "age_min")
    Integer ageMin;

    @Column(name = "age_max")
    Integer ageMax;

    // Tag món ăn dùng cho comparator IN_SET/NOT_IN_SET khi targetType = FOOD_TAG
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "nutrition_rule_food_tags",
            joinColumns = @JoinColumn(
                    name = "rule_id",
                    foreignKey = @ForeignKey(name = "fk_rule_food_tags_rule")
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 128)
    @Builder.Default
    Set<FoodTag> foodTags = new HashSet<>();

    @Column(name = "message", nullable = false, length = 1000)
    String message;

    @Column(name = "source", length = 512)
    String source;

    // Audit
    @CreationTimestamp
    @Column(name = "create_at", updatable = false, nullable = false)
    Instant createAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    Instant updateAt;
}
