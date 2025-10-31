package com.hn.nutricarebe.dto.overview;

import com.hn.nutricarebe.entity.Food;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

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
