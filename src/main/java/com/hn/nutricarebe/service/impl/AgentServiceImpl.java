package com.hn.nutricarebe.service.impl;

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

import java.util.HashMap;
import java.util.Map;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AgentServiceImpl implements AgentService {
    ToolRegistry toolRegistry;
    Planner planner;
//    MealPlannerService mealPlannerService;
    ProfileService profileService;



    @Override
    public MealPlanDto createPlan(PlanRequest req) {
//        var profile = profileService.getByUserId(req.getUserId());
//        return mealPlannerService.generatePlan(profile, req);
        return new MealPlanDto();
    }

    @Override
    public AgentResponse chat(AgentRequest req) {
        //Truyền message vào planner để tạo kế hoạch
        Plan p = planner.plan(req.getMessage());

        Object lastData = null;
        for (var step : p.getSteps()) {
            if ("generate_meal_plan".equals(step.name)) {
                Map<String,Object> args = new HashMap<>(step.args);
                args.put("userId", req.getUserId());
                var result = toolRegistry.invoke("generate_meal_plan", args);
                lastData = result; // chứa {"plan": MealPlanDto}
            }
        }

        String reply = (p.getHumanSummary()==null || p.getHumanSummary().isBlank())
                ? "Mình đã thực hiện yêu cầu."
                : p.getHumanSummary();

        return new AgentResponse(reply, lastData);
    }

}
