package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NutritionAudit {
    String dishName;
    String servingName;
    BigDecimal servingGram;
    List<IngredientBreakdown> items; // bảng chi tiết từng nguyên liệu
    Nutrition totalFromDB;           // tổng dinh dưỡng tính từ DB
}
