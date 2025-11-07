package com.hn.nutricarebe.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonthlyWeeklyNutritionDto {
    int weekIndex; // Tuần 1..(4/5)
    LocalDate weekStart; // Thứ 2 đầu tuần (clipped trong tháng)
    LocalDate weekEnd; // Chủ nhật cuối tuần (clipped trong tháng)

    BigDecimal proteinG;
    BigDecimal carbG;
    BigDecimal fatG;
    BigDecimal fiberG;

    int daysWithLogs; // số ngày có log trong tuần (để UI hiển thị)
}
