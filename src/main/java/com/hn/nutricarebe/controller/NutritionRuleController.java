package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.service.NutritionRuleService;
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

    @GetMapping("/{id}")
    public ApiResponse<NutritionRule> getById(@PathVariable UUID id) {
        return ApiResponse.<NutritionRule>builder()
                .message("Lấy quy tắc dinh dưỡng thành công")
                .data(nutritionRuleService.getById(id))
                .build();
    }
}
