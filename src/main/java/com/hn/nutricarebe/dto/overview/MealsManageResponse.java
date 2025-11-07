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
public class MealsManageResponse {
    long countNewFoodsInLastWeek;
    long totalFoods;
    long countLogsFromPlanSource;
    long countLogsFromScanSource;
    long countLogsFromManualSource;
    List<FoodLogStatDto> getTop10FoodsFromPlan;
}
