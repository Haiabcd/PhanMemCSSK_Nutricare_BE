package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.AgentRequest;
import com.hn.nutricarebe.dto.request.PlanRequest;
import com.hn.nutricarebe.dto.response.AgentResponse;
import com.hn.nutricarebe.dto.response.MealPlanDto;

public interface AgentService {
    MealPlanDto createPlan(PlanRequest req);
    AgentResponse chat(AgentRequest req);
}
