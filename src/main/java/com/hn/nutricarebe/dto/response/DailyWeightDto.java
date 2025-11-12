package com.hn.nutricarebe.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyWeightDto {
    LocalDate date;
    Integer weightKg;
}
