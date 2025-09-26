package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanResponse;
import com.hn.nutricarebe.entity.MealPlanDay;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.ActivityLevel;
import com.hn.nutricarebe.enums.GoalType;
import com.hn.nutricarebe.mapper.MealPlanDayMapper;
import com.hn.nutricarebe.repository.MealPlanDayRepository;
import com.hn.nutricarebe.service.MealPlanDayService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanDayServiceImpl implements MealPlanDayService {

    MealPlanDayRepository mealPlanDayRepository;
    MealPlanDayMapper mealPlanDayMapper;


    @Override
    public MealPlanResponse createPlan(MealPlanCreationRequest request) {
        final double MIN_KCAL_FEMALE = 1200.0;
        final double MIN_KCAL_MALE   = 1500.0;
        final double FAT_PCT = 0.30;                    // WHO: ≤30%
        final double FREE_SUGAR_PCT_MAX = 0.10;         // WHO: <10%
        final int    SODIUM_MG_LIMIT = 2000;            // WHO: <2000 mg natri
        final double WATER_ML_PER_KG = 35.0;            // 30–35 ml/kg
        final double MAX_DAILY_ADJ   = 1000.0;          // ±1000 kcal/ngày

        var profile = request.getProfile();
        int currentYear = java.time.Year.now().getValue();
        int age    = Math.max(0, currentYear - profile.getBirthYear());
        int weight = Math.max(1, profile.getWeightKg());
        int height = Math.max(50, profile.getHeightCm());

        // 1) BMR: Mifflin–St Jeor
        double bmr = switch (profile.getGender()) {
            case MALE   -> 10 * weight + 6.25 * height - 5 * age + 5;
            case FEMALE -> 10 * weight + 6.25 * height - 5 * age - 161;
            case OTHER  -> 10 * weight + 6.25 * height - 5 * age;
        };

        // 2) TDEE theo mức độ hoạt động (guard null)
        ActivityLevel al = profile.getActivityLevel() != null ? profile.getActivityLevel() : ActivityLevel.SEDENTARY;
        double activityFactor = switch (al) {
            case SEDENTARY         -> 1.2;
            case LIGHTLY_ACTIVE    -> 1.375;
            case MODERATELY_ACTIVE -> 1.55;
            case VERY_ACTIVE       -> 1.725;
            case EXTRA_ACTIVE      -> 1.9;
        };
        double tdee = bmr * activityFactor;

        // 3) Điều chỉnh kcal theo mục tiêu cân nặng (delta kg dương=tăng, âm=giảm)
        Integer deltaKg = profile.getTargetWeightDeltaKg();
        Integer weeks   = profile.getTargetDurationWeeks();
        double dailyAdj = 0.0;
        boolean hasDelta = (deltaKg != null && deltaKg != 0) && (weeks != null && weeks > 0);
        if (hasDelta && profile.getGoal() != GoalType.MAINTAIN) {
            dailyAdj = (deltaKg * 7700.0) / (weeks * 7.0); // kcal/ngày, mang dấu theo delta
            dailyAdj = Math.max(-MAX_DAILY_ADJ, Math.min(MAX_DAILY_ADJ, dailyAdj));
        }

        // Nếu MAINTAIN, bỏ qua delta
        double targetCalories = (profile.getGoal() == GoalType.MAINTAIN) ? tdee : (tdee + dailyAdj);

        // 3b) Mức kcal tối thiểu theo giới
        targetCalories = switch (profile.getGender()){
            case FEMALE -> Math.max(MIN_KCAL_FEMALE, targetCalories);
            case MALE   -> Math.max(MIN_KCAL_MALE,   targetCalories);
            case OTHER  -> Math.max(MIN_KCAL_FEMALE, targetCalories);
        };

        // 4) Nước
        double waterMl = weight * WATER_ML_PER_KG;

        // 5) Protein theo g/kg (WHO ≥0.8; giảm mỡ/tăng cơ 1.0–1.2)
        double proteinPerKg = switch (profile.getGoal()) {
            case MAINTAIN -> 0.8;
            case LOSE     -> 1.0;
            case GAIN     -> 1.2;
        };
        double proteinG = weight * proteinPerKg;
        double proteinKcal = proteinG * 4.0;

        // 6) Fat: 30% năng lượng (không vượt chuẩn WHO)
        double fatKcal = targetCalories * FAT_PCT;
        double fatG = fatKcal / 9.0;

        // 7) Carb = phần còn lại
        double carbKcal = Math.max(0.0, targetCalories - proteinKcal - fatKcal);
        double carbG = carbKcal / 4.0;

        // 8) Fiber: tối thiểu 25g; nâng theo 14g/1000kcal nếu kcal cao
        double fiberG = Math.max(25.0, 14.0 * (targetCalories / 1000.0));

        // 9) Free sugar trần <10% năng lượng (lưu mg)
        double sugarGMax = (targetCalories * FREE_SUGAR_PCT_MAX) / 4.0;
        int sugarMg = (int) Math.round(sugarGMax * 1000.0);

        Nutrition target = Nutrition.builder()
                .kcal((int) Math.round(targetCalories))
                .proteinG(bd(proteinG, 2))
                .carbG(bd(carbG, 2))
                .fatG(bd(fatG, 2))
                .fiberG(bd(fiberG, 2))
                .sodiumMg(SODIUM_MG_LIMIT)
                .sugarMg(bd(sugarMg,2))
                .build();

        MealPlanDay plan = MealPlanDay.builder()
                .user(User.builder().id(request.getUserId()).build())
                .targetNutrition(target)
                .date(LocalDate.now())
                .waterTargetMl((int) Math.round(waterMl))
                .build();

        return mealPlanDayMapper.toMealPlanResponse(mealPlanDayRepository.save(plan));
    }

    private static BigDecimal bd(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }


}
