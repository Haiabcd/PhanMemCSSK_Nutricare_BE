package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.hn.nutricarebe.enums.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionRuleResponse {
    UUID id;
    RuleType ruleType;
    RuleScope scope;
    TargetType targetType;
    String targetCode;
    Comparator comparator;
    BigDecimal thresholdMin;
    BigDecimal thresholdMax;
    Boolean perKg;
    Integer frequencyPerScope;
    Gender applicableSex;
    Integer ageMin;
    Integer ageMax;
    String message;
    String source;
    List<String> tags;
}
