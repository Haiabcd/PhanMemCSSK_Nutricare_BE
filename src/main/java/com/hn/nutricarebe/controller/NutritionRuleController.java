package com.hn.nutricarebe.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.NutritionRuleUpdateDto;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.service.NutritionRuleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/nutrition-rules")
public class NutritionRuleController {
    NutritionRuleService nutritionRuleService;

    @GetMapping("/{id}")
    public ApiResponse<NutritionRule> getById(@PathVariable UUID id) {
        return ApiResponse.<NutritionRule>builder()
                .message("Lấy quy tắc dinh dưỡng thành công")
                .data(nutritionRuleService.getById(id))
                .build();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteById(@PathVariable UUID id) {
        nutritionRuleService.deleteById(id);
        return ApiResponse.<Void>builder()
                .message("Xóa quy tắc dinh dưỡng thành công")
                .build();
    }

    @PutMapping("/{id}")
    public ApiResponse<Void> update(@PathVariable UUID id, @RequestBody @Valid NutritionRuleUpdateDto dto) {
        nutritionRuleService.update(id, dto);
        return ApiResponse.<Void>builder()
                .message("Cập nhật quy tắc dinh dưỡng thành công")
                .build();
    }
}
