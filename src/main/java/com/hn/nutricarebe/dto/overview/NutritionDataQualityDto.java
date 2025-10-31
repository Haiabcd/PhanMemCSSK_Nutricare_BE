package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NutritionDataQualityDto {
    long totalFoods;
    long completeMacros;   // đủ kcal, proteinG, carbG, fatG
    long missingMacros;    // = total - complete
    long highEnergyFoods;  // kcal >= high (mặc định 800)
    long lowEnergyFoods;   // kcal < low (mặc định 300)
    double  completenessRate; // phần trăm (0..100)
}
