package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.StatisticHelper.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.hn.nutricarebe.entity.MealPlanDay;
import com.hn.nutricarebe.repository.MealPlanDayRepository;
import com.hn.nutricarebe.repository.WeightLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class StatisticsServiceImpl implements StatisticsService {
    ProfileService profileService;
    PlanLogService planLogService;
    WaterLogService waterLogService;
    MealPlanDayService mealPlanDayService;
    WeightLogRepository weightLogRepository;
    MealPlanDayRepository mealPlanDayRepository;

    @Override
    public StatisticWeekResponse byWeek() {
        UUID userId = getCurrentUserId();
        ProfileCreationRequest p = profileService.findByUserId_request(userId);
        double bmi = calculateBmi(p);
        String bmiClassification = classifyBmi(bmi);

        WeekRange range = getCurrentWeekRange();
        LocalDate today = LocalDate.now();

        List<TopFoodDto> topFoods =
                planLogService.getTopFoods(userId, range.start(), range.end(), 5);

        List<DailyNutritionDto> dailyNutrition =
                planLogService.getDailyNutrition(userId, range.start(), range.end(), true);

        Map<MealSlot, Map<String, Long>> mealSlotSummary =
                planLogService.getMealSlotSummary(userId, range.start(), range.end());

        List<DailyWaterTotalDto> dailyWaterTotals =
                waterLogService.getDailyTotals(userId, range.start(), range.end(), true);

        // Nước thực tế theo ngày
        Map<LocalDate, Long> waterActualMap = dailyWaterTotals.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyWaterTotalDto::getDate,
                        d -> d.getTotalMl() == null ? 0L : d.getTotalMl()
                ));

        // Nước mục tiêu theo ngày từ MealPlanDay
        var mealPlanDays = mealPlanDayRepository
                .findByUser_IdAndDateBetweenOrderByDateAsc(userId, range.start(), range.end());

        Map<LocalDate, Integer> waterTargetMap = mealPlanDays.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MealPlanDay::getDate,
                        MealPlanDay::getWaterTargetMl
                ));

        List<DayTarget> dayTargets =
                mealPlanDayService.getDayTargetsBetween(range.start(), range.end(), userId);

        List<DayConsumedTotal> consumedTotals =
                planLogService.getConsumedTotalsBetween(range.start(), range.end(), userId);

        Map<LocalDate, DayConsumedTotal> consumedMap =
                consumedTotals.stream()
                        .collect(java.util.stream.Collectors.toMap(DayConsumedTotal::getDate, d -> d));

        List<String> warnings = new ArrayList<>();

        for (DayTarget target : dayTargets) {
            LocalDate date = target.getDate();
            if (date.isAfter(today)) continue;

            List<String> parts = new ArrayList<>();

            // Ăn uống (kcal)
            DayConsumedTotal consumed = consumedMap.get(date);
            if (consumed == null) {
                parts.add("Không có dữ liệu ăn uống nào được ghi lại");
            } else {
                String kcalBody = compareDayBody(target, consumed);
                if (kcalBody != null) parts.add(kcalBody);
            }

            // Nước
            Integer targetWater = waterTargetMap.get(date);
            Long actualWater = waterActualMap.get(date);
            long actual = (actualWater == null ? 0L : actualWater);

            if (targetWater != null && targetWater > 0) {
                long deficit = targetWater - actual;
                if (deficit > 0) {
                    parts.add(String.format(
                            "Thiếu nước %d ml (uống %d/%d ml)",
                            deficit, actual, targetWater
                    ));
                }
            } else {
                if (actual == 0L) {
                    parts.add("Không có dữ liệu nước nào được ghi lại");
                }
            }

            if (!parts.isEmpty()) {
                warnings.add("Ngày " + date + ": " + String.join(" và ", parts));
            }
        }

        var logs = weightLogRepository
                .findByProfile_User_IdAndLoggedAtBetweenOrderByLoggedAt(userId, range.start(), range.end());

        List<DailyWeightDto> weeklyWeightTrend = logs.stream()
                .map(wl -> DailyWeightDto.builder()
                        .date(wl.getLoggedAt())
                        .weightKg(wl.getWeightKg())
                        .build())
                .toList();

        return StatisticWeekResponse.builder()
                .weightKg(p.getWeightKg())
                .bmiClassification(bmiClassification)
                .bmi(bmi)
                .topFoods(topFoods)
                .dailyNutrition(dailyNutrition)
                .mealSlotSummary(mealSlotSummary)
                .dailyWaterTotals(dailyWaterTotals)
                .weeklyWeightTrend(weeklyWeightTrend)
                .warnings(warnings)
                .build();
    }

    @Override
    public StatisticMonthResponse byMonth() {
        UUID userId = getCurrentUserId();
        ProfileCreationRequest p = profileService.findByUserId_request(userId);
        double bmi = calculateBmi(p);
        String bmiClassification = classifyBmi(bmi);

        MonthRange range = getCurrentMonthRange();
        LocalDate today = LocalDate.now();
        LocalDate effectiveEnd = range.end().isAfter(today) ? today : range.end();

        List<TopFoodDto> topFoods =
                planLogService.getTopFoods(userId, range.start(), range.end(), 5);

        Map<MealSlot, Map<String, Long>> mealSlotSummary =
                planLogService.getMealSlotSummary(userId, range.start(), range.end());

        List<DailyNutritionDto> dailyNutrition =
                planLogService.getDailyNutrition(userId, range.start(), range.end(), true);

        List<MonthlyWeeklyNutritionDto> weeklyNutrition =
                aggregateMonthByWeeks(dailyNutrition, range.start(), range.end());

        List<DailyWaterTotalDto> dailyWaterTotals =
                waterLogService.getDailyTotals(userId, range.start(), range.end(), true);

        List<MonthlyWeeklyWaterTotalDto> weeklyWaterTotals =
                aggregateWaterMonthByWeeks(dailyWaterTotals, range.start(), range.end());

        // Nước thực tế theo ngày
        Map<LocalDate, Long> waterActualMap = dailyWaterTotals.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyWaterTotalDto::getDate,
                        d -> d.getTotalMl() == null ? 0L : d.getTotalMl()
                ));

        // Nước mục tiêu theo ngày (MealPlanDay.waterTargetMl)
        var mealPlanDays = mealPlanDayRepository
                .findByUser_IdAndDateBetweenOrderByDateAsc(userId, range.start(), range.end());

        Map<LocalDate, Integer> waterTargetMap = mealPlanDays.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MealPlanDay::getDate,
                        MealPlanDay::getWaterTargetMl
                ));

        List<DayTarget> dayTargets =
                mealPlanDayService.getDayTargetsBetween(range.start(), range.end(), userId);

        List<DayConsumedTotal> consumedTotals =
                planLogService.getConsumedTotalsBetween(range.start(), range.end(), userId);

        Map<LocalDate, DayConsumedTotal> consumedMap =
                consumedTotals.stream()
                        .collect(java.util.stream.Collectors.toMap(DayConsumedTotal::getDate, d -> d));

        List<DayTarget> dayTargetsUptoToday = dayTargets.stream()
                .filter(d -> !d.getDate().isAfter(effectiveEnd))
                .toList();

        // Cảnh báo theo tuần (compact): kcal + thiếu nước bao nhiêu ml
        List<String> weeklyWarnings =
                warningsByWeekCompact(
                        dayTargetsUptoToday,
                        consumedMap,
                        waterActualMap,
                        waterTargetMap,
                        range.start(),
                        effectiveEnd,
                        2
                );

        var logs = weightLogRepository
                .findByProfile_User_IdAndLoggedAtBetweenOrderByLoggedAt(userId, range.start(), range.end());

        List<DailyWeightDto> weeklyWeightTrend = logs.stream()
                .map(wl -> DailyWeightDto.builder()
                        .date(wl.getLoggedAt())
                        .weightKg(wl.getWeightKg())
                        .build())
                .toList();

        return StatisticMonthResponse.builder()
                .weightKg(p.getWeightKg())
                .bmiClassification(bmiClassification)
                .bmi(bmi)
                .topFoods(topFoods)
                .weeklyNutrition(weeklyNutrition)
                .mealSlotSummary(mealSlotSummary)
                .weeklyWaterTotals(weeklyWaterTotals)
                .warnings(weeklyWarnings)
                .weeklyWeightTrend(weeklyWeightTrend)
                .build();
    }
    @Override
    public StatisticWeekResponse byRange(LocalDate start, LocalDate end) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        if (start == null || end == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        LocalDate from = start;
        LocalDate to = end;
        if (from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
        }

        UUID userId = UUID.fromString(auth.getName());
        ProfileCreationRequest p = profileService.findByUserId_request(userId);
        double bmi = calculateBmi(p);
        String bmiClassification = classifyBmi(bmi);

        List<TopFoodDto> topFoods =
                planLogService.getTopFoods(userId, from, to, 5);

        List<DailyNutritionDto> dailyNutrition =
                planLogService.getDailyNutrition(userId, from, to, true);

        Map<MealSlot, Map<String, Long>> mealSlotSummary =
                planLogService.getMealSlotSummary(userId, from, to);

        List<DailyWaterTotalDto> dailyWaterTotals =
                waterLogService.getDailyTotals(userId, from, to, true);

        // Nước thực tế theo ngày
        Map<LocalDate, Long> waterActualMap = dailyWaterTotals.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DailyWaterTotalDto::getDate,
                        d -> d.getTotalMl() == null ? 0L : d.getTotalMl()
                ));

        // Nước mục tiêu theo ngày
        var mealPlanDays = mealPlanDayRepository
                .findByUser_IdAndDateBetweenOrderByDateAsc(userId, from, to);

        Map<LocalDate, Integer> waterTargetMap = mealPlanDays.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MealPlanDay::getDate,
                        MealPlanDay::getWaterTargetMl
                ));

        List<DayTarget> dayTargets =
                mealPlanDayService.getDayTargetsBetween(from, to, userId);

        List<DayConsumedTotal> consumedTotals =
                planLogService.getConsumedTotalsBetween(from, to, userId);

        Map<LocalDate, DayConsumedTotal> consumedMap =
                consumedTotals.stream()
                        .collect(java.util.stream.Collectors.toMap(DayConsumedTotal::getDate, d -> d));

        List<String> warnings = new ArrayList<>();

        for (DayTarget target : dayTargets) {
            LocalDate date = target.getDate();

            List<String> parts = new ArrayList<>();

            // Ăn uống (kcal)
            DayConsumedTotal consumed = consumedMap.get(date);
            if (consumed == null) {
                parts.add("Không có dữ liệu ăn uống nào được ghi lại");
            } else {
                String kcalBody = compareDayBody(target, consumed);
                if (kcalBody != null) parts.add(kcalBody);
            }

            // Nước
            Integer targetWater = waterTargetMap.get(date);
            Long actualWater = waterActualMap.get(date);
            long actual = (actualWater == null ? 0L : actualWater);

            if (targetWater != null && targetWater > 0) {
                long deficit = targetWater - actual;
                if (deficit > 0) {
                    parts.add(String.format(
                            "Thiếu nước %d ml (uống %d/%d ml)",
                            deficit, actual, targetWater
                    ));
                }
            } else {
                if (actual == 0L) {
                    parts.add("Không có dữ liệu nước nào được ghi lại");
                }
            }

            if (!parts.isEmpty()) {
                warnings.add("Ngày " + date + ": " + String.join(" và ", parts));
            }
        }

        var logs = weightLogRepository
                .findByProfile_User_IdAndLoggedAtBetweenOrderByLoggedAt(userId, from, to);

        List<DailyWeightDto> weeklyWeightTrend = logs.stream()
                .map(wl -> DailyWeightDto.builder()
                        .date(wl.getLoggedAt())
                        .weightKg(wl.getWeightKg())
                        .build())
                .toList();

        return StatisticWeekResponse.builder()
                .weightKg(p.getWeightKg())
                .bmiClassification(bmiClassification)
                .bmi(bmi)
                .topFoods(topFoods)
                .dailyNutrition(dailyNutrition)
                .mealSlotSummary(mealSlotSummary)
                .dailyWaterTotals(dailyWaterTotals)
                .weeklyWeightTrend(weeklyWeightTrend)
                .warnings(warnings)
                .build();
    }

    //====================================== Helpers =====================================//
    public record MonthRange(LocalDate start, LocalDate end) {}

    public record WeekRange(LocalDate start, LocalDate end) {}

    private double calculateBmi(ProfileCreationRequest p) {
        double heightM = p.getHeightCm() / 100.0; // cm -> m
        return p.getWeightKg() / (heightM * heightM); // kg / m^2
    }

    private String classifyBmi(double bmi) {
        if (bmi < 18.5) return "Thiếu cân";
        if (bmi < 23.0) return "Bình thường";
        if (bmi < 25.0) return "Tiền béo phì";
        if (bmi < 30.0) return "Thừa cân  độ I";
        if (bmi < 35.0) return "Béo phì độ II";
        return "Béo phì độ III";
    }

    private UUID getCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return UUID.fromString(auth.getName());
    }
}
