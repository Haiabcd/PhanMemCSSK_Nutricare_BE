package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.LogResponse;
import com.hn.nutricarebe.service.PlanLogService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;


@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/logs")
public class MealLogController {
    PlanLogService foodLogService;

    @PostMapping("/plan")
    public ApiResponse<Void> savePlanLog(@RequestBody @Valid SaveLogRequest req) {
        foodLogService.savePlanLog(req);
        return ApiResponse.<Void>builder()
                .message("Ghi log theo kế hoạch thành công")
                .build();
    }

    @GetMapping
    public ApiResponse<List<LogResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.<List<LogResponse>>builder()
                .message("Lấy log theo ngày thành công")
                .data(foodLogService.getByDate(date))
                .build();
    }
}
