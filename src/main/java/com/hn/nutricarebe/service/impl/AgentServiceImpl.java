package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.ai.memory.MemoryStore;
import com.hn.nutricarebe.ai.planner.Plan;
import com.hn.nutricarebe.ai.planner.Planner;
import com.hn.nutricarebe.ai.tools.ToolRegistry;
import com.hn.nutricarebe.dto.request.AgentRequest;
import com.hn.nutricarebe.dto.request.PlanRequest;
import com.hn.nutricarebe.dto.response.AgentResponse;
import com.hn.nutricarebe.dto.response.MealPlanDto;
import com.hn.nutricarebe.service.AgentService;
import com.hn.nutricarebe.service.ProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.UUID;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AgentServiceImpl implements AgentService {
    ToolRegistry toolRegistry;
    Planner planner;
//    MealPlannerService mealPlannerService;
    ProfileService profileService;
    MemoryStore memoryStore;



    @Override
    public MealPlanDto createPlan(PlanRequest req) {
//        var profile = profileService.getByUserId(req.getUserId());
//        return mealPlannerService.generatePlan(profile, req);
        return new MealPlanDto();
    }

    @Override
    public AgentResponse chat(AgentRequest req) {
        UUID userId = req.getUserId();

        // 1) nạp facts
        var facts = memoryStore.loadFacts(userId);

        // 2) planner dùng facts làm mặc định
        Plan p = planner.plan(req.getMessage(), facts);

        Object lastData = null;
        for (var step : p.getSteps()) {
            if ("generate_meal_plan".equals(step.name)) {
                var args = new java.util.HashMap<String,Object>(step.args);
                args.put("userId", userId);
                var result = toolRegistry.invoke("generate_meal_plan", args);
                lastData = result;

                // 3) lưu lại default “học” được
                Object mpd = step.args.get("mealsPerDay");
                if (mpd != null) memoryStore.upsertFact(userId, "mealsPerDay", mpd);
                if (step.args.get("focusTags") != null)
                    memoryStore.upsertFact(userId, "focusTags", step.args.get("focusTags"));
                if (step.args.get("avoidTags") != null)
                    memoryStore.upsertFact(userId, "avoidTags", step.args.get("avoidTags"));
            }
            if ("evaluate_meal".equals(step.name)) {
                var args = new java.util.HashMap<String,Object>(step.args);
                args.put("userId", req.getUserId());
                var result = toolRegistry.invoke("evaluate_meal", args);
                lastData = result; // {"evaluation": MealEvaluationDto}
            }
        }

        String reply = (p.getHumanSummary()==null || p.getHumanSummary().isBlank())
                ? "Mình đã thực hiện yêu cầu."
                : p.getHumanSummary();

        return new AgentResponse(reply, lastData);
    }

}
