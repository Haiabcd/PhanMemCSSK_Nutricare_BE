package com.hn.nutricarebe.service;

import java.util.List;
import java.util.UUID;

import com.hn.nutricarebe.dto.ai.CreationRuleAI;
import com.hn.nutricarebe.dto.ai.NutritionRuleAI;
import com.hn.nutricarebe.dto.request.NutritionRuleUpdateDto;
import com.hn.nutricarebe.entity.NutritionRule;

public interface NutritionRuleService {
    NutritionRule getById(UUID id);

    List<NutritionRule> getRuleByUserId(UUID userId);

    void saveRules(CreationRuleAI request, List<NutritionRuleAI> rules);

    void deleteById(UUID id);

    void update(UUID id, NutritionRuleUpdateDto dto);
}
