package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.enums.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NutritionRuleUpdateDto {
    // AVOID(tránh),LIMIT(giới hạn),PREFER(khuyến khích)
    @NotNull(message = "Loại rule không được để trống")
    RuleType ruleType;
    // ITEM(từng món), MEAL(bữa), DAY(ngày)
    @NotNull(message = "Phạm vi rule không được để trống")
    RuleScope scope;
    // Mã đích: NUTRIENT; FOOD_TAG
    @NotNull(message = "Loại đích không được để trống")
    TargetType targetType;
    // Chỉ có giá trị khi targetType = NUTRIENT chỉ thuộc các (PROTEIN, CARB,FAT,FIBER, SODIUM,SUGAR,WATER)
    String targetCode;
    //LT,LTE,EQ,GTE,GT,BETWEEN
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
    String source;
    Boolean active;
    // Tag món ăn có sẵn trong hệ thống
    @Builder.Default
    Set<UUID> foodTags = new HashSet<>();
    String message;
}
