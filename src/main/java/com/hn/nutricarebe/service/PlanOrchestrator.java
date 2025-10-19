package com.hn.nutricarebe.service;

import java.time.LocalDate;
import java.util.UUID;

public interface PlanOrchestrator {
    void updatePlan(LocalDate date, UUID userId);

}
