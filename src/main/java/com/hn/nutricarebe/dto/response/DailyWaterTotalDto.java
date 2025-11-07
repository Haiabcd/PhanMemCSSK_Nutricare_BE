package com.hn.nutricarebe.dto.response;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DailyWaterTotalDto {
    LocalDate date;
    Long totalMl;

    // (tuỳ chọn) nới lỏng, phòng trường hợp driver trả Number khác Long
    public DailyWaterTotalDto(LocalDate date, Number totalMl) {
        this.date = date;
        this.totalMl = totalMl == null ? 0L : totalMl.longValue();
    }
}
