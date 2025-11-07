package com.hn.nutricarebe.dto.overview;

import java.time.LocalDate;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailyCountDto {
    String dayLabel; // "Thứ 2", "Thứ 3", ...
    LocalDate date; // 2025-10-27
    long total; // 5
}
