package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.enums.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;


@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionRuleCreationRequest {
    // Liên kết (có thể null nếu rule mang tính chung)
    UUID conditionId;
    UUID allergyId;

    @NotNull(message = "ruleType là bắt buộc")
    RuleType ruleType;

    @NotNull(message = "scope là bắt buộc")
    RuleScope scope;

    @NotNull(message = "targetType là bắt buộc")
    private TargetType targetType;

    @NotBlank(message = "targetCode là bắt buộc")
    @Size(max = 128, message = "targetCode tối đa 128 ký tự")
    private String targetCode;

    // Toán tử & ngưỡng
    @NotNull(message = "comparator là bắt buộc")
    private Comparator comparator;


    @Digits(integer = 8, fraction = 4, message = "thresholdMin không hợp lệ (tối đa 8 số nguyên và 4 số thập phân)")
    private BigDecimal thresholdMin;

    @Digits(integer = 8, fraction = 4, message = "thresholdMax không hợp lệ (tối đa 8 số nguyên và 4 số thập phân)")
    private BigDecimal thresholdMax;


    // Tính theo kg thể trọng
    @NotNull(message = "perKg là bắt buộc")
    private Boolean perKg = Boolean.FALSE;

    // Tần suất trong 1 scope (nếu có)
    @PositiveOrZero(message = "frequencyPerScope phải >= 0")
    private Integer frequencyPerScope;

    @NotNull(message = "active là bắt buộc")
    private Boolean active = Boolean.TRUE;

    private Gender applicableSex;
    @Min(value = 0, message = "ageMin phải >= 0")
    private Integer ageMin;
    @Min(value = 0, message = "ageMax phải >= 0")
    private Integer ageMax;


    // Tập tag dùng cho comparator IN_SET / NOT_IN_SET
    @Builder.Default
    private Set<FoodTag> foodTags = new HashSet<>();

    // Thông điệp & nguồn
    @NotBlank(message = "message là bắt buộc")
    @Size(max = 1000, message = "message tối đa 1000 ký tự")
    private String message;

    @Size(max = 512, message = "source tối đa 512 ký tự")
    private String source;



    /** BETWEEN → cần cả min & max và min <= max. */
    @AssertTrue(message = "BETWEEN cần thresholdMin và thresholdMax, và thresholdMin <= thresholdMax")
    public boolean isBetweenThresholdValid() {
        if (comparator == null) return true;
        if (comparator != Comparator.BETWEEN) return true;
        if (thresholdMin == null || thresholdMax == null) return false;
        return thresholdMin.compareTo(thresholdMax) <= 0;
    }

    /** LTE/LT → cần Max; GTE/GT → cần Min. */
    @AssertTrue(message = "Comparator yêu cầu ngưỡng phù hợp: LTE/LT cần thresholdMax; GTE/GT cần thresholdMin")
    public boolean isUnaryThresholdPresent() {
        if (comparator == null) return true;
        switch (comparator) {
            case LTE, LT -> {
                return thresholdMax != null; }
            case GTE, GT -> {
                return thresholdMin != null; }
            default -> {
                return true; }
        }
    }

    /** IN_SET/NOT_IN_SET → yêu cầu có ít nhất 1 tag tương ứng. */
    @AssertTrue(message = "IN_SET/NOT_IN_SET yêu cầu cung cấp ít nhất một tag (foodTags/ingredientTags)")
    public boolean isTagsProvidedForSetComparators() {
        if (comparator == null) return true;
        if (comparator != Comparator.IN_SET && comparator != Comparator.NOT_IN_SET) return true;
        return foodTags != null && !foodTags.isEmpty();
    }

    /** Kiểm tra khoảng tuổi nếu cả hai đều có. */
    @AssertTrue(message = "ageMin phải <= ageMax")
    public boolean isAgeRangeValid() {
        if (ageMin == null || ageMax == null) return true;
        return ageMin <= ageMax;
    }
}
