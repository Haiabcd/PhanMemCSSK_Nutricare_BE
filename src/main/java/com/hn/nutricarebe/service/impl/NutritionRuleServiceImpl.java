package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.mapper.NutritionRuleMapper;
import com.hn.nutricarebe.repository.AllergyRepository;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.repository.NutritionRuleRepository;
import com.hn.nutricarebe.service.NutritionRuleService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class NutritionRuleServiceImpl implements NutritionRuleService {
    NutritionRuleRepository nutritionRuleRepository;
    ConditionRepository conditionRepository;
    AllergyRepository allergyRepository;
    NutritionRuleMapper nutritionRuleMapper;


    @Override
    @Transactional
    public boolean save(NutritionRuleCreationRequest request) {
        NutritionRule entity = nutritionRuleMapper.toNutritionRule(request);

        if (request.getConditionId() != null) {
            Condition condition = conditionRepository.findById(request.getConditionId()).orElse(null);
            entity.setCondition(condition);
        }
        if (request.getAllergyId() != null) {
            Allergy allergy = allergyRepository.findById(request.getAllergyId()).orElse(null);
            entity.setAllergy(allergy);
        }
        NutritionRule nr =  nutritionRuleRepository.save(entity);
        if(nr == null) {
            return false;
        }
        return true;
    }
}
