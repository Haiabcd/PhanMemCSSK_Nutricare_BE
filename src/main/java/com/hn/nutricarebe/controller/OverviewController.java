package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.overview.*;
import com.hn.nutricarebe.service.FoodService;
import com.hn.nutricarebe.service.impl.OverviewServiceImpl;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/overview")
public class OverviewController {
    OverviewServiceImpl overviewService;

    /** ====== 1) Tổng quan hệ thống ====== */
    @GetMapping
    public OverviewResponse getOverview() {
        return overviewService.getOverview();
    }

    /** ====== 2) Quản lý bệnh nền & dị ứng ====== */
    @GetMapping("/clinical")
    public ClinicalResponse clinical() {
        return overviewService.clinical();
    }

    /** ====== 3) Quản lý người dùng ====== */
    @GetMapping("/users")
    public UserManageResponse userManage() {
        return overviewService.userManage();
    }

    /** ====== 4) Quản lý món ăn ====== */
    @GetMapping("/meals")
    public MealsManageResponse mealsManage() {
        return overviewService.mealsManage();
    }

    /** ====== 5) Quản lý dinh dưỡng ===== */
    @GetMapping("/nutrition")
    public NutritionManageResponse nutritionManage() {
        return overviewService.nutritionManage();
    }

    /** ====== 6) Quản lý nguyên liệu ====== */
    @GetMapping("/ingredients")
    public IngredientManageResponse ingredientManage() {
        return overviewService.ingredientManage();
    }

}
