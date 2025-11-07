package com.hn.nutricarebe.dto.response;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MonthlyWeeklyWaterTotalDto {
    int weekIndex; // 1..(4/5)
    LocalDate weekStart; // Thứ 2 (đã cắt trong phạm vi tháng)
    LocalDate weekEnd; // Chủ nhật (đã cắt trong phạm vi tháng)
    long totalMl; // tổng ml trong tuần
    int daysWithLogs; // số ngày có log trong tuần
}
