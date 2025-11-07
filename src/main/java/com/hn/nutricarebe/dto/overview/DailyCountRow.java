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
public class DailyCountRow {
    LocalDate day;
    long total;
}
