package com.hn.nutricarebe.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.StatisticMonthResponse;
import com.hn.nutricarebe.dto.response.StatisticWeekResponse;
import com.hn.nutricarebe.service.StatisticsService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;

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

    @GetMapping("/range")
    public ApiResponse<StatisticWeekResponse> byRange(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return ApiResponse.<StatisticWeekResponse>builder()
                .message("Thống kê theo khoảng thành công")
                .data(statisticsService.byRange(start, end))
                .build();
    }
}
