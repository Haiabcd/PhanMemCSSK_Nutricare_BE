package com.hn.nutricarebe.dto.overview;

import java.time.YearMonth;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MonthlyCountDto {
    String monthLabel;
    int month;
    long total;
    YearMonth yearMonth;
}
