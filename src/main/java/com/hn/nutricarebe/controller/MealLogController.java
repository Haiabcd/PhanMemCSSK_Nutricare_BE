package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.PlanLogManualRequest;
import com.hn.nutricarebe.dto.request.PlanLogUpdateRequest;
import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.LogResponse;
import com.hn.nutricarebe.entity.PlanLog;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.service.PlanLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;



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
            @RequestParam @NotNull
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date,
            @RequestParam @NotNull
            MealSlot mealSlot
    ) {
        List<LogResponse> data = logService.getLog(date, mealSlot);
        return ApiResponse.<List<LogResponse>>builder()
                .message("Lấy danh sách log theo ngày thành công")
                .data(data)
                .build();
    }

    @GetMapping("/nutriLog")
    public ApiResponse<NutritionResponse> getNutritionLogByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ApiResponse.<NutritionResponse>builder()
                .message("Lấy log theo kế hoạch thành công")
                .data(logService.getNutritionLogByDate(date))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlanLog(@PathVariable UUID id) {
        logService.deleteById(id);
        return ApiResponse.<Void>builder()
                .message("Xóa PlanLog thành công")
                .build();
    }

    @PostMapping("/save/manual")
    public ApiResponse<Void> create(
            @Valid @RequestBody PlanLogManualRequest request
    ) {
        logService.savePlanLog_Manual(request);
        return ApiResponse.<Void>builder()
                .message("Tạo log thủ công thành công")
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> updatePlanLog(
            @PathVariable("id") UUID planLogId,
            @Valid @RequestBody PlanLogUpdateRequest req
    ) {
        logService.updatePlanLog(req, planLogId);
        return ApiResponse.<Void>builder()
                .message("Cập nhật PlanLog thành công")
                .build();
    }
}
