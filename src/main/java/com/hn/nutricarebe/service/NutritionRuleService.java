package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;
import com.hn.nutricarebe.entity.NutritionRule;

import java.util.List;
import java.util.UUID;

public interface NutritionRuleService {
    boolean save(NutritionRuleCreationRequest request);
    NutritionRule getById(UUID id);
    List<NutritionRule> getRuleByUserId(UUID userId);
}
