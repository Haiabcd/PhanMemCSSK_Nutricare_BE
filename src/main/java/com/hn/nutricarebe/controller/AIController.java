package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.AgentRequest;
import com.hn.nutricarebe.dto.request.PlanRequest;
import com.hn.nutricarebe.dto.response.AgentResponse;
import com.hn.nutricarebe.dto.response.MealPlanDto;
import com.hn.nutricarebe.service.AgentService;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor
public class AIController {
    AgentService agentService;

    @PostMapping("/plan")
    public ResponseEntity<MealPlanDto> plan(@RequestBody PlanRequest req) {
        if (req.getUserId() == null || req.getUserId().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (req.getDays() <= 0 || req.getDays() > 14) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(agentService.createPlan(req));
    }

    @PostMapping("/chat")
    public ResponseEntity<AgentResponse> chat(@RequestBody AgentRequest req) {
        if (req.getUserId()==null || req.getUserId().isBlank() || req.getMessage()==null) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(agentService.chat(req));
    }
}
