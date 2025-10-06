package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.service.NutritionRuleService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/nutrition-rules")
public class NutritionRuleController {
    NutritionRuleService nutritionRuleService;

    @PostMapping("/save")
    public ApiResponse<?> create(@Valid @RequestBody NutritionRuleCreationRequest request) {
        boolean isSuccess = nutritionRuleService.save(request);
        if (isSuccess) {
            return ApiResponse.builder()
                    .message("Tạo quy tắc dinh dưỡng thành công")
                    .build();
        } else {
            return ApiResponse.builder()
                    .message("Tạo quy tắc dinh dưỡng thất bại")
                    .build();
        }
    }

    @GetMapping("/{id}")
    public ApiResponse<NutritionRule> getById(@PathVariable UUID id) {
        return ApiResponse.<NutritionRule>builder()
                .message("Lấy quy tắc dinh dưỡng thành công")
                .data(nutritionRuleService.getById(id))
                .build();
    }
}
