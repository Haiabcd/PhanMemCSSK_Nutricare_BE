package com.hn.nutricarebe.dto.overview;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.YearMonth;

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
