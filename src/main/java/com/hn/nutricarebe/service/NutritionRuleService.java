package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;
import com.hn.nutricarebe.entity.NutritionRule;

import java.util.UUID;

public interface NutritionRuleService {
    public boolean save(NutritionRuleCreationRequest request);
    public NutritionRule getById(UUID id);
}
