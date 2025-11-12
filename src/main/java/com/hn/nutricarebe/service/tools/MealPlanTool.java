package com.hn.nutricarebe.service.tools;

import static com.hn.nutricarebe.helper.MealPlanHelper.caculateNutrition;
import static com.hn.nutricarebe.helper.MealPlanHelper.deriveAggregateConstraintsFromRules;


import java.util.*;
import com.hn.nutricarebe.dto.ai.*;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.service.NutritionRuleService;
import com.hn.nutricarebe.service.ProfileService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanTool {
    NutritionRuleService nutritionRuleService;
    ProfileService profileService;
    FoodRepository foodRepository;

    @Tool(
            name = "calcBmi",
            description =
                    "Tính BMI dựa trên hồ sơ hiện tại; có thể override weightKg/heightCm tạm thời cho lần tính này.")
    public Map<String, Object> calcBmi(Integer overrideWeightKg, Integer overrideHeightCm) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        ProfileCreationRequest p = profileService.findByUserId_request(userId);
        int h = (overrideHeightCm != null ? overrideHeightCm : p.getHeightCm());
        int w = (overrideWeightKg != null ? overrideWeightKg : p.getWeightKg());
        double m = Math.max(1, h) / 100.0;
        double bmi = w / (m * m);

        String category = (bmi < 18.5)
                ? "Thiếu cân"
                : (bmi < 23)
                        ? "Bình thường (theo chuẩn Châu Á)"
                        : (bmi < 25)
                                ? "Tiền béo phì (theo chuẩn Châu Á)"
                                : (bmi < 30) ? "Béo phì độ I (theo chuẩn Châu Á)" : "Béo phì II (theo chuẩn Châu Á)";

        return Map.of(
                "chieuCaoCm", h,
                "canNangKg", w,
                "bmi", Math.round(bmi * 10.0) / 10.0,
                "phanLoai", category);
    }

    @Tool(
            name = "getDailyTargets",
            description = "Tính mục tiêu dinh dưỡng/ngày theo công thức hệ thống; cho phép override tạm thời profile.")
    public DailyTargetsAI getDailyTargets(DailyTargetsOverrides ov) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        final double WATER_ML_PER_KG = 35.0;
        ProfileCreationRequest base = profileService.findByUserId_request(userId);
        ProfileCreationRequest eff = ProfileCreationRequest.builder()
                .heightCm((ov != null && ov.getHeightCm() != null) ? ov.getHeightCm() : base.getHeightCm())
                .weightKg((ov != null && ov.getWeightKg() != null) ? ov.getWeightKg() : base.getWeightKg())
                .targetWeightDeltaKg(
                        (ov != null && ov.getTargetWeightDeltaKg() != null)
                                ? ov.getTargetWeightDeltaKg()
                                : base.getTargetWeightDeltaKg())
                .targetDurationWeeks(
                        (ov != null && ov.getTargetDurationWeeks() != null)
                                ? ov.getTargetDurationWeeks()
                                : base.getTargetDurationWeeks())
                .gender((ov != null && ov.getGender() != null) ? ov.getGender() : base.getGender())
                .birthYear((ov != null && ov.getBirthYear() != null) ? ov.getBirthYear() : base.getBirthYear())
                .goal((ov != null && ov.getGoal() != null) ? ov.getGoal() : base.getGoal())
                .activityLevel(
                        (ov != null && ov.getActivityLevel() != null) ? ov.getActivityLevel() : base.getActivityLevel())
                .name(base.getName())
                .build();
        int weight = Math.max(1, eff.getWeightKg());
        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        AggregateConstraints agg = deriveAggregateConstraintsFromRules(rules, weight);
        double waterMl = weight * WATER_ML_PER_KG;
        if (agg.dayWaterMin != null) waterMl = Math.max(waterMl, agg.dayWaterMin.doubleValue());
        Nutrition target = caculateNutrition(eff, agg);
        return DailyTargetsAI.builder()
                .targets(target)
                .waterMl((int) Math.round(waterMl))
                .build();
    }

    @Tool(
            name = "createMealPlanningContext",
            description =
                    "Trả về context lập kế hoạch: days, effective profile (đã áp overrides), daily targets, water, rules, và 1 trang foods đầu. KHÔNG lưu DB.")
    public Map<String, Object> createMealPlanningContext(
            Integer days,
            DailyTargetsOverrides ov,
            Integer foodsLimit,
            String foodsCursor,
            String slot,
            String keyword) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        var userId = java.util.UUID.fromString(auth.getName());
        // targets
        DailyTargetsAI daily = getDailyTargets(ov);
        // foods page đầu
        var page = getFoodsPage(foodsLimit, foodsCursor, slot, keyword);
        // rules
        var rules = nutritionRuleService.getRuleByUserId(userId);
        // profile hiệu lực (cho LLM biết demographics khi áp rule MEAL/ITEM if any)
        var base = profileService.findByUserId_request(userId);
        var eff = ProfileCreationRequest.builder()
                .heightCm(ov != null && ov.getHeightCm() != null ? ov.getHeightCm() : base.getHeightCm())
                .weightKg(ov != null && ov.getWeightKg() != null ? ov.getWeightKg() : base.getWeightKg())
                .targetWeightDeltaKg(
                        ov != null && ov.getTargetWeightDeltaKg() != null
                                ? ov.getTargetWeightDeltaKg()
                                : base.getTargetWeightDeltaKg())
                .targetDurationWeeks(
                        ov != null && ov.getTargetDurationWeeks() != null
                                ? ov.getTargetDurationWeeks()
                                : base.getTargetDurationWeeks())
                .gender(ov != null && ov.getGender() != null ? ov.getGender() : base.getGender())
                .birthYear(ov != null && ov.getBirthYear() != null ? ov.getBirthYear() : base.getBirthYear())
                .goal(ov != null && ov.getGoal() != null ? ov.getGoal() : base.getGoal())
                .activityLevel(
                        ov != null && ov.getActivityLevel() != null ? ov.getActivityLevel() : base.getActivityLevel())
                .name(base.getName())
                .build();
        return Map.of(
                "days",
                (days != null && days > 0) ? days : 7,
                "effectiveProfile",
                eff,
                "dailyTargets",
                daily,
                "rules",
                rules,
                "foods",
                page,
                "slotKcalPct",
                Map.of( // NEW
                        "BREAKFAST", 0.25,
                        "LUNCH", 0.30,
                        "DINNER", 0.30,
                        "SNACK", 0.15),
                "slotItemCounts",
                Map.of( // NEW
                        "BREAKFAST", 2,
                        "LUNCH", 3,
                        "DINNER", 3,
                        "SNACK", 1),
                "units",
                Map.of(
                        "kcal",
                        "kcal",
                        "proteinG",
                        "g",
                        "carbG",
                        "g",
                        "fatG",
                        "g",
                        "fiberG",
                        "g",
                        "sodiumMg",
                        "mg",
                        "sugarMg",
                        "mg"));
    }

    @Tool(
            name = "getFoodsCandidatesByKcalWindow",
            description = "Lấy danh sách ứng viên theo slot và cửa sổ kcal quanh perItemTargetKcal. "
                    + "Params: slot(BREAKFAST/LUNCH/DINNER/SNACK), perItemTargetKcal(int), "
                    + "lowMul(default=0.5), highMul(default=2.0), limit(default=80).")
    public PlanningContextAI.FoodsPage getFoodsCandidatesByKcalWindow(
            String slot, Integer perItemTargetKcal, Double lowMul, Double highMul, Integer limit) {
        int lim = (limit == null || limit <= 0) ? 80 : limit;
        double lo = (lowMul == null || lowMul <= 0) ? 0.5 : lowMul;
        double hi = (highMul == null || highMul <= 0) ? 2.0 : highMul;

        int pivot = (perItemTargetKcal == null || perItemTargetKcal <= 0) ? 300 : perItemTargetKcal;
        int minK = Math.max(20, (int) Math.round(pivot * lo));
        int maxK = Math.max(minK + 10, (int) Math.round(pivot * hi));

        var list = foodRepository.selectCandidatesBySlotAndKcalWindow(slot, minK, maxK, pivot, lim);
        var items = list.stream().map(this::toFoodLite).toList();

        return PlanningContextAI.FoodsPage.builder()
                .items(items)
                .limit(lim)
                .nextCursor(null)
                .hasNext(false)
                .build();
    }

    @Tool(
            name = "getFoodsPage",
            description = "Trả về 1 TRANG món ăn từ CSDL (để AI chọn món). "
                    + "Tham số: limit (mặc định 40), cursor ('0' = trang đầu), "
                    + "slot (optional: BREAKFAST/LUNCH/DINNER/SNACK), keyword (optional).")
    public PlanningContextAI.FoodsPage getFoodsPage(Integer limit, String cursor, String slot, String keyword) {

        int lim = (limit == null || limit <= 0) ? 40 : limit;
        int pageIdx = 0;
        try {
            if (cursor != null) pageIdx = Integer.parseInt(cursor);
        } catch (Exception ignored) {
        }

        var pageable = PageRequest.of(pageIdx, lim);

        // Ưu tiên lọc theo slot hoặc keyword nếu có
        var slice = (slot != null && !slot.isBlank())
                ? foodRepository.findByMealSlot(MealSlot.valueOf(slot), pageable)
                : (keyword != null && !keyword.isBlank())
                        ? foodRepository.findByNameContainingIgnoreCase(keyword, pageable)
                        : foodRepository.findAllBy(pageable);
        var items = slice.getContent().stream().map(this::toFoodLite).toList();
        boolean hasNext = slice.hasNext();
        String nextCursor = hasNext ? String.valueOf(pageIdx + 1) : null;

        return PlanningContextAI.FoodsPage.builder()
                .items(items)
                .limit(lim)
                .nextCursor(nextCursor)
                .hasNext(hasNext)
                .build();
    }


    private static Double toD(java.math.BigDecimal x) {
        return x == null ? null : x.doubleValue();
    }

    private PlanningContextAI.FoodLite toFoodLite(Food f) {
        var n = f.getNutrition();
        return PlanningContextAI.FoodLite.builder()
                .foodId(f.getId())
                .name(f.getName())
                .slotHints(f.getMealSlots().stream().map(Enum::name).toList())
                .kcal(n != null && n.getKcal() != null ? n.getKcal().intValue() : null)
                .proteinG(toD(n != null ? n.getProteinG() : null))
                .carbG(toD(n != null ? n.getCarbG() : null))
                .fatG(toD(n != null ? n.getFatG() : null))
                .fiberG(toD(n != null ? n.getFiberG() : null))
                .sodiumMg(n != null && n.getSodiumMg() != null ? n.getSodiumMg().intValue() : null)
                .sugarMg(n != null && n.getSugarMg() != null ? n.getSugarMg().intValue() : null)
                .tags(
                        f.getTags() != null
                                ? f.getTags().stream().map(Tag::getNameCode).toList()
                                : List.of())
                .defaultServing(f.getDefaultServing())
                .servingName(f.getServingName())
                .servingGram(toD(f.getServingGram()))
                .cookMinutes(f.getCookMinutes())
                .imageKey(f.getImageKey())
                .description(f.getDescription())
                .build();
    }
}
