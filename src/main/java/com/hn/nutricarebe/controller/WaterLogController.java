package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.service.WaterLogService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/water-logs")
public class WaterLogController {
    WaterLogService waterLogService;

    @PostMapping
    public ApiResponse<Void> createWaterLog(@RequestBody @Valid WaterLogCreationRequest request) {
        waterLogService.create(request);
        return ApiResponse.<Void>builder()
                .message("Tạo log nước thành công")
                .build();
    }
}
