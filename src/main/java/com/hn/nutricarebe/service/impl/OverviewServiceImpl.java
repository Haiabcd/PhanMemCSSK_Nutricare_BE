package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.overview.*;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.LogSource;
import com.hn.nutricarebe.service.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class OverviewServiceImpl {
    FoodService foodService;
    UserService userService;
    PlanLogService planLogService;
    UserConditionService userConditionService;
    UserAllergyService userAllergyService;
    AllergyService allergyService;
    ConditionService conditionService;
    ProfileService profileService;
    IngredientService ingredientService;

    // Tổng quan
    public OverviewResponse getOverview() {
        long totalUsers = userService.getTotalUsers();
        long totalFoods = foodService.getTotalFoods();
        List<DailyCountDto> dailyCount  = userService.getNewUsersThisWeek();
        List<MonthlyCountDto> monthlyCount  = foodService.getNewFoodsByMonth();
        Map<String, Long> getCountBySource = planLogService.getCountBySource();
        Map<String, Long> getPlanLogCountByMealSlot = planLogService.getPlanLogCountByMealSlot();

        return OverviewResponse.builder()
                .totalUsers(totalUsers)
                .totalFoods(totalFoods)
                .dailyCount(dailyCount)
                .monthlyCount(monthlyCount)
                .getCountBySource(getCountBySource)
                .getPlanLogCountByMealSlot(getPlanLogCountByMealSlot)
                .build();
    }

    // Quan ly bệnh nền va di ung
    public ClinicalResponse clinical() {
        List<Map<String, Object>> top5Condition =   userConditionService.getTop5ConditionNames();
        List<Map<String, Object>> top5Allergy =   userAllergyService.getTop5AllergyNames();
        long getTotalAllergies = allergyService.getTotalAllergies();
        long getTotalConditions = conditionService.getTotalConditions();

        return  ClinicalResponse.builder()
                .top5Condition(top5Condition)
                .top5Allergy(top5Allergy)
                .getTotalAllergies(getTotalAllergies)
                .getTotalConditions(getTotalConditions)
                .build();

    }

    // Quan ly nguoi dung
    public UserManageResponse userManage(){
        long totalUsers = userService.getTotalUsers();
        long getNewUsersInLast7Days = userService.getNewUsersInLast7Days();
        Map<String, Long> getUserRoleCounts = userService.getUserRoleCounts();
        Map<String, Long> getGoalStats = profileService.getGoalStats();
        List<TopUserDto> getTopUsersByLogCount = planLogService.getTopUsersByLogCount();
        Map<String, Long> countUsersByStatus = userService.countUsersByStatus();

        return UserManageResponse.builder()
                .totalUsers(totalUsers)
                .getNewUsersInLast7Days(getNewUsersInLast7Days)
                .getUserRoleCounts(getUserRoleCounts)
                .getGoalStats(getGoalStats)
                .getTopUsersByLogCount(getTopUsersByLogCount)
                .countUsersByStatus(countUsersByStatus)
                .build();
    }

    // quản lý món ăn
    public MealsManageResponse mealsManage(){
        long countNewFoodsInLastWeek = foodService.countNewFoodsInLastWeek();
        long totalFoods = foodService.getTotalFoods();
        long countLogsFromPlanSource = planLogService.countLogsFromPlanSource(LogSource.PLAN);
        long countLogsFromScanSource = planLogService.countLogsFromPlanSource(LogSource.SCAN);
        long countLogsFromManualSource = planLogService.countLogsFromPlanSource(LogSource.MANUAL);
        List<FoodLogStatDto> getTop10FoodsFromPlan = planLogService.getTop10FoodsFromPlan();

        return MealsManageResponse.builder()
                .countNewFoodsInLastWeek(countNewFoodsInLastWeek)
                .totalFoods(totalFoods)
                .countLogsFromPlanSource(countLogsFromPlanSource)
                .countLogsFromScanSource(countLogsFromScanSource)
                .countLogsFromManualSource(countLogsFromManualSource)
                .getTop10FoodsFromPlan(getTop10FoodsFromPlan)
                .build();
    }

    // quản lý dinh dưỡng
    public NutritionManageResponse nutritionManage(){
        long countFoodsUnder300Kcal = foodService.countFoodsWithLowKcal();
        long countFoodsOver800Kcal = foodService.countFoodsWithHighKcal();
        long countFoodsWithComplete5 = foodService.countFoodsWithComplete5();
        long totalFoods = foodService.getTotalFoods();
        double getDataCompletenessRate = foodService.getDataCompletenessRate();
        List<FoodTopKcalDto> getTop10HighestKcalFoods = foodService.getTop10HighKcalFoods();
        List<FoodTopProteinDto> getTop10HighestProteinFoods = foodService.getTop10HighProteinFoods();
        EnergyHistogramDto getEnergyHistogramFixed = foodService.getEnergyHistogramFixed();

        return NutritionManageResponse.builder()
                .countFoodsUnder300Kcal(countFoodsUnder300Kcal)
                .countFoodsOver800Kcal(countFoodsOver800Kcal)
                .countFoodsWithComplete5(countFoodsWithComplete5)
                .totalFoods(totalFoods)
                .getDataCompletenessRate(getDataCompletenessRate)
                .getTop10HighestKcalFoods(getTop10HighestKcalFoods)
                .getTop10HighestProteinFoods(getTop10HighestProteinFoods)
                .getEnergyHistogramFixed(getEnergyHistogramFixed)
                .build();
    }

    // quản lý nguyên liệu
    public IngredientManageResponse ingredientManage(){
        long countIngredients = ingredientService.countIngredients();
        long countNewIngredientsThisWeek = ingredientService.countNewIngredientsThisWeek();

        return IngredientManageResponse.builder()
                .countIngredients(countIngredients)
                .countNewIngredientsThisWeek(countNewIngredientsThisWeek)
                .build();
    }

}
