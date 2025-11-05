package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.hn.nutricarebe.helper.StatisticHelper.*;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class StatisticsServiceImpl implements StatisticsService {
    ProfileService profileService;
    PlanLogService planLogService;
    WaterLogService waterLogService;
    MealPlanDayService mealPlanDayService;


    @Override
    public StatisticWeekResponse byWeek() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        ProfileCreationRequest p = profileService.findByUserId_request(userId);
        double bmi = calculateBmi(p);
        String bmiClassification = classifyBmi(bmi);

        WeekRange range = getCurrentWeekRange();

        List<TopFoodDto> topFoods = planLogService.getTopFoods(userId, range.start, range.end, 5);

        List<DailyNutritionDto> dailyNutrition = planLogService.getDailyNutrition(userId, range.start, range.end, true);

        Map<MealSlot, Map<String, Long>> mealSlotSummary = planLogService.getMealSlotSummary(userId, range.start, range.end);

        List<DailyWaterTotalDto> dailyWaterTotals = waterLogService.getDailyTotals(userId, range.start, range.end, true);

        List<DayTarget>  dayTargets = mealPlanDayService.getDayTargetsBetween(range.start, range.end, userId);
        List<DayConsumedTotal> consumedTotals  = planLogService.getConsumedTotalsBetween(range.start, range.end, userId);
        // Đưa consumedTotals vào map để tra nhanh
        Map<LocalDate, DayConsumedTotal> consumedMap = consumedTotals.stream()
                .collect(java.util.stream.Collectors.toMap(DayConsumedTotal::getDate, d -> d));
        // So sánh và sinh cảnh báo
        List<String> warnings = new java.util.ArrayList<>();
        for (DayTarget target : dayTargets) {
            DayConsumedTotal consumed = consumedMap.get(target.getDate());
            if (consumed == null) {
                warnings.add("Ngày " + target.getDate() + ": Không có dữ liệu ăn uống nào được ghi lại.");
                continue;
            }
            String msg = compareDay(target, consumed);
            if (msg != null) warnings.add(msg);
        }
        return StatisticWeekResponse.builder()
                .weightKg(p.getWeightKg())
                .bmiClassification(bmiClassification)
                .bmi(bmi)
                .topFoods(topFoods)
                .dailyNutrition(dailyNutrition)
                .mealSlotSummary(mealSlotSummary)
                .dailyWaterTotals(dailyWaterTotals)
                .warnings(warnings)
                .build();
    }

    @Override
    public StatisticMonthResponse byMonth() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        ProfileCreationRequest p = profileService.findByUserId_request(userId);
        double bmi = calculateBmi(p);

        String bmiClassification = classifyBmi(bmi);

        MonthRange range = getCurrentMonthRange();

        // Top món, thống kê theo ngày, theo slot, và nước
        List<TopFoodDto> topFoods = planLogService.getTopFoods(userId, range.start, range.end, 5);
        Map<MealSlot, Map<String, Long>> mealSlotSummary = planLogService.getMealSlotSummary(userId, range.start, range.end);

        List<DailyNutritionDto> dailyNutrition = planLogService.getDailyNutrition(userId, range.start, range.end, true);
        List<MonthlyWeeklyNutritionDto> weeklyNutrition =
                aggregateMonthByWeeks(dailyNutrition, range.start, range.end);

        // === NƯỚC: gộp theo tuần ===
        List<DailyWaterTotalDto> dailyWaterTotals = waterLogService.getDailyTotals(userId, range.start, range.end, true);
        List<MonthlyWeeklyWaterTotalDto> weeklyWaterTotals =
                aggregateWaterMonthByWeeks(dailyWaterTotals, range.start, range.end);

       // === CẢNH BÁO: gọn theo tuần ===
        List<DayTarget> dayTargets = mealPlanDayService.getDayTargetsBetween(range.start, range.end, userId);
        List<DayConsumedTotal> consumedTotals = planLogService.getConsumedTotalsBetween(range.start, range.end, userId);
        Map<LocalDate, DayConsumedTotal> consumedMap = consumedTotals.stream()
                .collect(java.util.stream.Collectors.toMap(DayConsumedTotal::getDate, d -> d));

        // mỗi tuần 1 câu tóm tắt gọn
        List<String> weeklyWarnings = warningsByWeekCompact(dayTargets, consumedMap, range.start, range.end, /*topPerWeek*/2);

        return StatisticMonthResponse.builder()
                .weightKg(p.getWeightKg())
                .bmiClassification(bmiClassification)
                .bmi(bmi)
                .topFoods(topFoods)
                .weeklyNutrition(weeklyNutrition)
                .mealSlotSummary(mealSlotSummary)
                .weeklyWaterTotals(weeklyWaterTotals)
                .warnings(weeklyWarnings)
                .build();
    }

    public record MonthRange(LocalDate start, LocalDate end) {}
    public record WeekRange(LocalDate start, LocalDate end) {}

    private double calculateBmi(ProfileCreationRequest p) {
        double heightM = p.getHeightCm() / 100.0;            // cm -> m
        return p.getWeightKg() / (heightM * heightM);        // kg / m^2
    }

    private String classifyBmi(double bmi) {
        if (bmi < 18.5) return "Thiếu cân";
        if (bmi < 23.0) return "Bình thường";
        if (bmi < 25.0) return "Tiền béo phì";
        if (bmi < 30.0) return "Thừa cân  độ I";
        if (bmi < 35.0) return "Béo phì độ II";
        return "Béo phì độ III";
    }

}
