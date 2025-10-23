package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.entity.PlanLog;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.service.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.hn.nutricarebe.helper.MealPlanHelper.caculateBMI;
import static com.hn.nutricarebe.helper.PlanLogHelper.aggregateActual;

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
        double bmi = caculateBMI(p);
        WeekRange range = getCurrentWeekRange();

        List<TopFoodDto> topFoods = planLogService.getTopFoods(userId, range.start, range.end, 5);

        List<DailyNutritionDto> dailyNutrition = planLogService.getDailyNutrition(userId, range.start, range.end, true);

        Map<MealSlot, Map<String, Long>> mealSlotSummary = planLogService.getMealSlotSummary(userId, range.start, range.end);

        List<DailyWaterTotalDto> dailyWaterTotals = waterLogService.getDailyTotals(userId, range.start, range.end, true);

//        double targetKcal = mealPlanDayService.getMealTargetKcal(userId, req.getMealSlot());
//        boolean nowDate = logOld.getDate().isEqual(LocalDate.now());
//        List<PlanLog> logAllDay = logRepository.findByUser_IdAndDate(userId, logOld.getDate());
//        NutritionResponse nr =  aggregateActual(logAllDay);
//        double actualKcal = nr.getKcal() != null ? nr.getKcal().doubleValue() : 0.0;

        return StatisticWeekResponse.builder()
                .weightKg(p.getWeightKg())
                .goalType(p.getGoal())
                .bmi(bmi)
                .topFoods(topFoods)
                .dailyNutrition(dailyNutrition)
                .mealSlotSummary(mealSlotSummary)
                .dailyWaterTotals(dailyWaterTotals)
                .build();
    }

    public static WeekRange getCurrentWeekRange() {
        LocalDate today = LocalDate.now();
        // Tuần bắt đầu từ THỨ 2, kết thúc CHỦ NHẬT
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return new WeekRange(start, end);
    }
    public static record WeekRange(LocalDate start, LocalDate end) {}
}
