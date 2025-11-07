package com.hn.nutricarebe.dto.overview;

import java.util.List;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class NutritionManageResponse {
    long countFoodsUnder300Kcal;
    long countFoodsOver800Kcal;
    long countFoodsWithComplete5;
    long totalFoods;
    double getDataCompletenessRate;
    List<FoodTopKcalDto> getTop10HighestKcalFoods;
    List<FoodTopProteinDto> getTop10HighestProteinFoods;
    EnergyHistogramDto getEnergyHistogramFixed;
}
