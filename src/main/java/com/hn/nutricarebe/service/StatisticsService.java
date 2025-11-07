package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.response.StatisticMonthResponse;
import com.hn.nutricarebe.dto.response.StatisticWeekResponse;

public interface StatisticsService {
    StatisticWeekResponse byWeek();

    StatisticMonthResponse byMonth();
}
