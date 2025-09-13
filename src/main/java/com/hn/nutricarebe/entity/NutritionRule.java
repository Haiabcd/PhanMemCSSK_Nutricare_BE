package com.hn.nutricarebe.entity;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionRule {
    RuleType ruleType;
    TargetType targetType;
    NutrientCode targetCode;
    BigDecimal threshold;
    String unit;
    RuleScope scope;
    String rationale;
}
