package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.MealPlanItemService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/meal-plans")
public class MealPlanController {
    MealPlanDayService mealPlanDayService;
    MealPlanItemService mealPlanItemService;

    @GetMapping
    public ApiResponse<MealPlanResponse> getCurrentWeek( @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.<MealPlanResponse>builder()
                .message("Lấy thực đơn tuần hiện tại thành công")
                .data(mealPlanDayService.getMealPlanByDate(date))
                .build();
    }
    @GetMapping("/suggestions")
    public ApiResponse<Page<FoodResponse>> getUpcomingFoods(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size
    ) {
        return ApiResponse.<Page<FoodResponse>>builder()
                .message("Lấy danh sách món ăn gợi ý thành công")
                .data(mealPlanItemService.getUpcomingFoods(page, size))
                .build();
    }


    @PutMapping("/{itemId}/swap")
    public ApiResponse<Void> smartSwap(@PathVariable UUID itemId) {
        mealPlanItemService.smartSwapMealItem(itemId);
        return ApiResponse.<Void>builder()
                .message("Hoán đổi món ăn thành công")
                .build();
    }
}
