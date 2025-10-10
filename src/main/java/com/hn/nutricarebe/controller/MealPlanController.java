package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.service.MealPlanDayService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/meal-plans")
public class MealPlanController {
    MealPlanDayService mealPlanDayService;

    @GetMapping
    public ApiResponse<MealPlanResponse> getCurrentWeek( @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.<MealPlanResponse>builder()
                .message("Lấy thực đơn tuần hiện tại thành công")
                .data(mealPlanDayService.getMealPlanByDate(date))
                .build();
    }



}
