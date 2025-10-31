package com.hn.nutricarebe.dto.overview;


import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class OverviewResponse {
    long totalUsers;
    long totalFoods;
    List<DailyCountDto> dailyCount;
    List<MonthlyCountDto> monthlyCount;
    Map<String, Long> getCountBySource;
    Map<String, Long> getPlanLogCountByMealSlot;
}
