package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.overview.*;

public interface OverviewAdminService {
    IngredientManageResponse ingredientManage();
    NutritionManageResponse nutritionManage();
    MealsManageResponse mealsManage();
    UserManageResponse userManage();
    ClinicalResponse clinical();
    OverviewResponse getOverview();
}
