package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnergyHistogramDto {
    List<EnergyBinDto> bins;  // các dải kcal
    long total;               // tổng số món
    long maxBinCount;
}
