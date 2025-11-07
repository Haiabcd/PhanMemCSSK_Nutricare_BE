package com.hn.nutricarebe.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.PlanLogManualRequest;
import com.hn.nutricarebe.dto.request.PlanLogScanRequest;
import com.hn.nutricarebe.dto.request.PlanLogUpdateRequest;
import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.KcalWarningResponse;
import com.hn.nutricarebe.dto.response.LogResponse;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.service.PlanLogService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/logs")
public class MealLogController {
    PlanLogService logService;

    @PostMapping("/plan")
    public ApiResponse<Void> savePlanLog(@RequestBody @Valid SaveLogRequest req) {
        logService.savePlanLog(req);
        return ApiResponse.<Void>builder()
                .message("Ghi log theo kế hoạch thành công")
                .build();
    }

    @GetMapping
    public ApiResponse<List<LogResponse>> getLogs(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @NotNull MealSlot mealSlot) {
        List<LogResponse> data = logService.getLog(date, mealSlot);
        return ApiResponse.<List<LogResponse>>builder()
                .message("Lấy danh sách log theo ngày thành công")
                .data(data)
                .build();
    }

    @GetMapping("/nutriLog")
    public ApiResponse<NutritionResponse> getNutritionLogByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.<NutritionResponse>builder()
                .message("Lấy log theo kế hoạch thành công")
                .data(logService.getNutritionLogByDate(date))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlanLog(@PathVariable UUID id) {
        logService.deleteById(id);
        return ApiResponse.<Void>builder().message("Xóa PlanLog thành công").build();
    }

    @PostMapping("/save/manual")
    public ApiResponse<KcalWarningResponse> create(@Valid @RequestBody PlanLogManualRequest request) {

        return ApiResponse.<KcalWarningResponse>builder()
                .message("Ghi log thủ công thành công")
                .data(logService.savePlanLog_Manual(request))
                .build();
    }

    @PostMapping("/save/scan")
    public ApiResponse<KcalWarningResponse> createScan(@Valid @RequestBody PlanLogScanRequest request) {
        return ApiResponse.<KcalWarningResponse>builder()
                .message("Ghi log scan thành công")
                .data(logService.savePlanLog_Scan(request))
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<KcalWarningResponse> updatePlanLog(
            @PathVariable("id") UUID planLogId, @Valid @RequestBody PlanLogUpdateRequest req) {
        return ApiResponse.<KcalWarningResponse>builder()
                .message("Cập nhật PlanLog thành công")
                .data(logService.updatePlanLog(req, planLogId))
                .build();
    }
}
