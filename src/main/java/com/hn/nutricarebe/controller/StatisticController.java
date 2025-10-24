package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.StatisticMonthResponse;
import com.hn.nutricarebe.dto.response.StatisticWeekResponse;
import com.hn.nutricarebe.service.StatisticsService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequiredArgsConstructor
@RequestMapping("/statistics")
public class StatisticController {
    StatisticsService statisticsService;

    @GetMapping("/week")
    public ApiResponse<StatisticWeekResponse> byWeek() {
        return ApiResponse.<StatisticWeekResponse>builder()
                .message("Thống kê theo tuần thành công")
                .data(statisticsService.byWeek())
                .build();
    }

    @GetMapping("/month")
    public ApiResponse<StatisticMonthResponse> byMonth() {
        return ApiResponse.<StatisticMonthResponse>builder()
                .message("Thống kê theo tháng thành công")
                .data(statisticsService.byMonth())
                .build();
    }
}
