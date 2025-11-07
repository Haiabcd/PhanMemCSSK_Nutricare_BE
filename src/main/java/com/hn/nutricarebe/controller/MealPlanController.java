package com.hn.nutricarebe.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.enums.MealSlot;
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
                .message("Lấy thực đơn tuần hiện tại thành công")
                .data(mealPlanDayService.getMealPlanByDate(date))
                .build();
    }

    @PutMapping("/{itemId}/swap")
    public ApiResponse<Void> smartSwap(@PathVariable UUID itemId) {
        mealPlanItemService.smartSwapMealItem(itemId);
        return ApiResponse.<Void>builder().message("Hoán đổi món ăn thành công").build();
    }

    @GetMapping("/suggest")
    public ApiResponse<List<FoodResponse>> suggestAllowedFoods(
            @RequestParam(name = "slot", required = false) MealSlot slot,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        if (limit < 1 || limit > 100) {
            limit = 20;
        }
        List<FoodResponse> suggestions = mealPlanItemService.suggestAllowedFoodsInternal(slot, limit);
        return ApiResponse.<List<FoodResponse>>builder()
                .message("Gợi ý món ăn thành công")
                .data(suggestions)
                .build();
    }
}
