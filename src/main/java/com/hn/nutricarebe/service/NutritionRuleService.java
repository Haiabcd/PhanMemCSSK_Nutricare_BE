package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;

public interface NutritionRuleService {
    public boolean save(NutritionRuleCreationRequest request);
}
