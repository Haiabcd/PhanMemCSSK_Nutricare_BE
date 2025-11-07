package com.hn.nutricarebe.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.service.WaterLogService;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/water-logs")
public class WaterLogController {
    WaterLogService waterLogService;

    @PostMapping
    public ApiResponse<Void> createWaterLog(@RequestBody @Valid WaterLogCreationRequest request) {
        waterLogService.create(request);
        return ApiResponse.<Void>builder().message("Tạo log nước thành công").build();
    }

    @GetMapping("/log")
    public ApiResponse<Integer> getTotalWaterByDate(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        int totalAmount = waterLogService.getTotalAmountByDate(date);
        return ApiResponse.<Integer>builder()
                .data(totalAmount)
                .message("Lấy tổng lượng nước theo ngày thành công")
                .build();
    }
}
