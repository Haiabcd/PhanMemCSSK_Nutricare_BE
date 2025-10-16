package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.NutritionRuleMapper;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.NutritionRuleService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class NutritionRuleServiceImpl implements NutritionRuleService {
    NutritionRuleRepository nutritionRuleRepository;
    ConditionRepository conditionRepository;
    AllergyRepository allergyRepository;
    NutritionRuleMapper nutritionRuleMapper;
    UserConditionRepository userConditionRepository;
    UserAllergyRepository userAllergyRepository;


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

    @Override
    @Transactional
    public NutritionRule getById(UUID id) {
        NutritionRule nutritionRule = nutritionRuleRepository.findWithCollectionsById(id);
        if (nutritionRule == null) {
            throw new AppException(ErrorCode.NUTRITION_RULE_NOT_FOUND);
        }
        return nutritionRule;
    }


    @Override
    public List<NutritionRule> getRuleByUserId(UUID userId) {
        Set<UUID> conditionIds = new HashSet<>();
        userConditionRepository.findByUser_Id(userId)
                .forEach(uc -> conditionIds.add(uc.getCondition().getId()));
        Set<UUID> allergyIds = new HashSet<>();

        userAllergyRepository.findByUser_Id(userId)
                .forEach(ua -> allergyIds.add(ua.getAllergy().getId()));

        return nutritionRuleRepository.findActiveByConditionsOrAllergies(
                conditionIds, allergyIds, conditionIds.isEmpty(), allergyIds.isEmpty()
        );
    }
}
