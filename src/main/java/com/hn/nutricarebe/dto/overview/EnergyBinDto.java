package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class EnergyBinDto {
    String label;
    Integer minKcal;   // null nếu ">1200" hoặc "Thiếu kcal"
    Integer maxKcal;   // null nếu ">1200" hoặc "Thiếu kcal"
    long count;
}
