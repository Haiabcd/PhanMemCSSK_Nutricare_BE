package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.response.StatisticMonthResponse;
import com.hn.nutricarebe.dto.response.StatisticWeekResponse;

import java.time.LocalDate;

public interface StatisticsService {
    StatisticWeekResponse byWeek();
    StatisticMonthResponse byMonth();
    StatisticWeekResponse byRange(LocalDate start, LocalDate end);
}
