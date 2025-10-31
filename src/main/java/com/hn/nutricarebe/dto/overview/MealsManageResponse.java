package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

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
