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

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(
        name = "nutrition_rule",
        indexes = {
                @Index(name = "idx_rule_condition", columnList = "condition_id"),
                @Index(name = "idx_rule_target", columnList = "target_type,target_code"),
                @Index(name = "idx_rule_scope", columnList = "scope"),
                @Index(name = "idx_rule_active_priority", columnList = "active,priority")
        }
)
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

    @Column(name = "target_code", nullable = false, length = 128)
    String targetCode;

    // ====== So sánh & ngưỡng ======
    @Enumerated(EnumType.STRING)
    @Column(name = "comparator", nullable = false, length = 16)
    Comparator comparator;

    @Column(name = "threshold_min", precision = 12, scale = 4)
    BigDecimal thresholdMin;

    @Column(name = "threshold_max", precision = 12, scale = 4)
    BigDecimal thresholdMax;

    // Đơn vị & cơ sở dữ liệu dinh dưỡng
    @Enumerated(EnumType.STRING)
    @Column(name = "unit", length = 24)
    Unit unit;


    @Builder.Default
    @Column(name = "per_kg", nullable = false)
    Boolean perKg = Boolean.FALSE;

    @Column(name = "per_kg_factor", precision = 6, scale = 3)
    BigDecimal perKgFactor;

    @Column(name = "frequency_per_scope")
    Integer frequencyPerScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 24)
    RuleSeverity severity;

    @Builder.Default
    @Column(name = "priority", nullable = false)
    Integer priority = 0;

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


    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "nutrition_rule_ingredient_tags",
            joinColumns = @JoinColumn(
                    name = "rule_id",
                    foreignKey = @ForeignKey(name = "fk_rule_ingredient_tags_rule")
            )
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "tag_code", nullable = false, length = 128)
    @Builder.Default
    Set<IngredientTag> ingredientTags = new HashSet<>();

    @Column(name = "message", nullable = false, length = 1000)
    String message;

    @Column(name = "source", length = 512)
    String source;

    @CreationTimestamp
    @Column(name = "create_at", updatable = false, nullable = false)
    Instant createAt;

    @UpdateTimestamp
    @Column(name = "update_at")
    Instant updateAt;
}
