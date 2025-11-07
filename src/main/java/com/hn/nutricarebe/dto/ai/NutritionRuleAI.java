package com.hn.nutricarebe.dto.ai;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

import com.hn.nutricarebe.enums.*;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NutritionRuleAI {
    // AVOID(tránh),LIMIT(giới hạn),PREFER(khuyến khích)
    RuleType ruleType;

    // ITEM(từng món), MEAL(bữa), DAY(ngày)
    RuleScope scope;

    // Mã đích: NUTRIENT; FOOD_TAG
    TargetType targetType;

    // Chỉ có giá trị khi targetType = NUTRIENT chỉ thuộc các (PROTEIN, CARB,FAT,FIBER, SODIUM,SUGAR,WATER)
    String targetCode;

    // LT,LTE,EQ,GTE,GT,BETWEEN
    // comparator,thresholdMin,thresholdMax chỉ có giá trị khi targetType = NUTRIENT
    // Nếu comparator = BETWEEN thì cả thresholdMin và thresholdMax đều có giá trị
    // Nếu comparator = LT , LTE thì chỉ có thresholdMax có giá trị
    // Nếu comparator = GT , GTE thì chỉ có thresholdMin có giá trị
    // Nếu comparator = EQ thì cả thresholdMin và thresholdMax đều có giá trị và bằng nhau
    Comparator comparator;
    BigDecimal thresholdMin;
    BigDecimal thresholdMax;
    // Tính theo kg thể trọng (ví dụ: PROTEIN ≥ 1.0 g/kg/day)
    Boolean perKg = Boolean.FALSE;
    Integer frequencyPerScope;
    // Áp dụng rule cho đối tượng nào : MALE,FEMALE,OTHER
    Gender applicableSex;
    Integer ageMin;
    Integer ageMax;
    // Tag món ăn có sẵn trong hệ thống
    @Builder.Default
    Set<String> foodTags = new HashSet<>();
    // Tag món ăn mới
    @Builder.Default
    Set<TagCreationRequest> customFoodTags = new HashSet<>();

    String message;
}
