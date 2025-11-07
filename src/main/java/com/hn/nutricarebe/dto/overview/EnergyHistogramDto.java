package com.hn.nutricarebe.dto.overview;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnergyHistogramDto {
    List<EnergyBinDto> bins; // các dải kcal
    long total; // tổng số món
    long maxBinCount;
}
