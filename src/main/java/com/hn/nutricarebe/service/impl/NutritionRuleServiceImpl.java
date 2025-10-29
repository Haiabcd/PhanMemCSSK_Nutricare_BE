package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.ai.CreationRuleAI;
import com.hn.nutricarebe.dto.ai.NutritionRuleAI;
import com.hn.nutricarebe.dto.ai.TagCreationRequest;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.entity.Tag;
import com.hn.nutricarebe.enums.TargetType;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.*;
import com.hn.nutricarebe.service.NutritionRuleService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.util.*;


@Log4j2
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class NutritionRuleServiceImpl implements NutritionRuleService {
    NutritionRuleRepository nutritionRuleRepository;
    UserConditionRepository userConditionRepository;
    UserAllergyRepository userAllergyRepository;
    TagRepository tagRepository;

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
        Set<NutritionRule> result = new LinkedHashSet<>();
        if (!conditionIds.isEmpty()) {
            result.addAll(nutritionRuleRepository.findByActiveTrueAndCondition_IdIn(conditionIds));
        }
        if (!allergyIds.isEmpty()) {
            result.addAll(nutritionRuleRepository.findByActiveTrueAndAllergy_IdIn(allergyIds));
        }
        return new ArrayList<>(result);
    }

    @Override
    @Transactional
    public void saveRules(CreationRuleAI request, List<NutritionRuleAI> rules) {
        if (rules == null || rules.isEmpty()) return;
        List<NutritionRule> saveList = new ArrayList<>();
        for (NutritionRuleAI ruleAI : rules) {
            Set<Tag> tags = new HashSet<>();
            if(ruleAI.getTargetType() == TargetType.FOOD_TAG){
                if(ruleAI.getCustomFoodTags() != null && !ruleAI.getCustomFoodTags().isEmpty()){
                    List<Tag> tagSave = new ArrayList<>();
                    for(TagCreationRequest t : ruleAI.getCustomFoodTags()){
                        Tag newTag = Tag.builder()
                                .nameCode(t.getNameCode())
                                .description(t.getDescription())
                                .build();
                        tagSave.add(newTag);
                    }
                    List<Tag> savedTags = tagRepository.saveAllAndFlush(tagSave);
                    tags.addAll(savedTags);
                }
                if(ruleAI.getFoodTags() != null && !ruleAI.getFoodTags().isEmpty()){
                    Set<Tag> existingTags = tagRepository.findByNameCodeInIgnoreCase(ruleAI.getFoodTags());
                    tags.addAll(existingTags);
                }
            }
            NutritionRule creationRequest = NutritionRule.builder()
                    .allergy(request.getAllergyId() != null ?
                            Allergy.builder().id(request.getAllergyId()).build()
                            : null)
                    .condition(request.getConditionId() != null ?
                            Condition.builder().id(request.getConditionId()).build()
                            : null)
                    .active(true)
                    .comparator(ruleAI.getComparator())
                    .scope(ruleAI.getScope())
                    .ruleType(ruleAI.getRuleType())
                    .targetCode(ruleAI.getTargetCode())
                    .targetType(ruleAI.getTargetType())
                    .perKg(ruleAI.getPerKg())
                    .frequencyPerScope(ruleAI.getFrequencyPerScope())
                    .thresholdMin(ruleAI.getThresholdMin())
                    .thresholdMax(ruleAI.getThresholdMax())
                    .ageMax(ruleAI.getAgeMax())
                    .ageMin(ruleAI.getAgeMin())
                    .applicableSex(ruleAI.getApplicableSex())
                    .tags(tags)
                    .message(ruleAI.getMessage())
                    .source(null)
                    .build();
            saveList.add(creationRequest);
        }
        nutritionRuleRepository.saveAll(saveList);
    }

}
