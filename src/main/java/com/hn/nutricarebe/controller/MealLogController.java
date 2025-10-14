package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.service.PlanLogService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;



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

    @DeleteMapping("/plan")
    public ApiResponse<Void> deletePlanLog(@RequestBody @Valid SaveLogRequest req) {
        logService.deletePlanLog(req);
        return ApiResponse.<Void>builder()
                .message("Xoá log theo kế hoạch thành công")
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
}
