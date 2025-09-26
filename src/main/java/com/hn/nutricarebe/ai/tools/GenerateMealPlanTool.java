package com.hn.nutricarebe.ai.tools;

import com.hn.nutricarebe.dto.response.MealPlanDto;
import com.hn.nutricarebe.service.ProfileService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class GenerateMealPlanTool implements Tool{
//    MealPlannerService mealPlannerService;
    ProfileService profileService;

    @Override
    public String name() {
        return "generate_meal_plan";
    }

    @Override
    public Map<String, Object> execute(Map<String, Object> args) {
        String userId = (String) args.get("userId");
        Integer days = (Integer) args.getOrDefault("days", 7);
        Integer mealsPerDay = (Integer) args.getOrDefault("mealsPerDay", 3);

        @SuppressWarnings("unchecked")
        List<String> focusTags = (List<String>) args.get("focusTags");

        @SuppressWarnings("unchecked")
        List<String> avoidTags = (List<String>) args.get("avoidTags");

        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }

//        UserProfile profile = profileService.getByUserId(userId);
//        if (profile == null) {
//            throw new IllegalArgumentException("Chưa có hồ sơ người dùng (profile) để lập kế hoạch.");
//        }

//        PlanRequest req = new PlanRequest();
//        req.setUserId(userId);
//        req.setDays(days);
//        req.setMealsPerDay(mealsPerDay);
//        req.setFocusTags(focusTags);
//        req.setAvoidTags(avoidTags);
//
//        MealPlanDto plan = mealPlannerService.generatePlan(profile, req);

        MealPlanDto plan = new MealPlanDto();
        return Map.of("plan", plan);
    }
}
