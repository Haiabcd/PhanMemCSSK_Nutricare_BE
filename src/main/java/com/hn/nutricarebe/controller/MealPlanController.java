package com.hn.nutricarebe.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.hn.nutricarebe.dto.response.SwapSuggestion;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.MealPlanItemService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/meal-plans")
public class MealPlanController {
    MealPlanDayService mealPlanDayService;
    MealPlanItemService mealPlanItemService;

    @GetMapping
    public ApiResponse<MealPlanResponse> getMealPlan(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.<MealPlanResponse>builder()
                .message("Lấy kế hoạch thành công")
                .data(mealPlanDayService.getMealPlanByDate(date))
                .build();
    }

    @PutMapping("/{itemId}/swap")
    public ApiResponse<Void> smartSwap(@PathVariable UUID itemId) {
        mealPlanItemService.smartSwapMealItem(itemId);
        return ApiResponse.<Void>builder().message("Hoán đổi món ăn thành công").build();
    }

    @GetMapping("/suggest")
    public ApiResponse<List<SwapSuggestion>> suggestSwaps() {
        List<SwapSuggestion> suggestions = mealPlanItemService.suggest();
        return ApiResponse.<List<SwapSuggestion>>builder()
                .message("Lấy danh sách gợi ý thành công")
                .data(suggestions)
                .build();
    }

}
