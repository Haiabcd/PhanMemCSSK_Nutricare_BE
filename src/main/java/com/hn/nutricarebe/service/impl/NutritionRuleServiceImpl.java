package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.ai.CreationRuleAI;
import com.hn.nutricarebe.dto.ai.NutritionRuleAI;
import com.hn.nutricarebe.dto.ai.TagCreationRequest;
import com.hn.nutricarebe.dto.request.NutritionRuleUpdateDto;
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
import org.springframework.security.access.prepost.PreAuthorize;
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


    @Override
    @Transactional
    public void deleteById(UUID id) {
        NutritionRule rule = nutritionRuleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NUTRITION_RULE_NOT_FOUND));
         nutritionRuleRepository.delete(rule);
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void update(UUID id, NutritionRuleUpdateDto dto) {
        NutritionRule rule = nutritionRuleRepository.findWithCollectionsById(id);
        if (rule == null) {
            throw new AppException(ErrorCode.NUTRITION_RULE_NOT_FOUND);
        }
        // 1) Validate DTO theo targetType & comparator
        validateUpdateDto(dto);
        // 2) Cập nhật các trường chung
        rule.setRuleType(dto.getRuleType());
        rule.setScope(dto.getScope());
        rule.setActive(Boolean.TRUE.equals(dto.getActive()));
        rule.setApplicableSex(dto.getApplicableSex());
        rule.setAgeMin(dto.getAgeMin());
        rule.setAgeMax(dto.getAgeMax());
        rule.setFrequencyPerScope(dto.getFrequencyPerScope());
        rule.setSource(dto.getSource());
        rule.setMessage(dto.getMessage());
        // 3) Theo targetType: NUTRIENT vs FOOD_TAG
        rule.setTargetType(dto.getTargetType());

        if (dto.getTargetType() == TargetType.NUTRIENT) {
            // targetCode bắt buộc hợp lệ
            rule.setTargetCode(safeUpper(dto.getTargetCode()));
            rule.setComparator(dto.getComparator());
            rule.setThresholdMin(dto.getThresholdMin());
            rule.setThresholdMax(dto.getThresholdMax());
            rule.setPerKg(Boolean.TRUE.equals(dto.getPerKg()));
            // NUTRIENT: không dùng tags
            if (rule.getTags() != null) rule.getTags().clear();
        } else if (dto.getTargetType() == TargetType.FOOD_TAG) {
            rule.setTargetCode(null);
            rule.setComparator(null);
            rule.setThresholdMin(null);
            rule.setThresholdMax(null);
            rule.setPerKg(Boolean.FALSE);
            Set<Tag> newTags = new HashSet<>();
            if (dto.getFoodTags() != null && !dto.getFoodTags().isEmpty()) {
                List<Tag> found = tagRepository.findAllById(dto.getFoodTags());
                if (found.size() != dto.getFoodTags().size()) {
                    throw new AppException(ErrorCode.INVALID_ARGUMENT);
                }
                newTags.addAll(found);
            }
            rule.getTags().clear();
            rule.getTags().addAll(newTags);
        }
        nutritionRuleRepository.save(rule);
    }
    //=========================HELPER METHODS=========================//

    private static String safeUpper(String s) {
        return s == null ? null : s.trim().toUpperCase();
    }
    private static Set<String> allowedNutrients() {
        return Set.of("PROTEIN", "CARB", "FAT", "FIBER", "SODIUM", "SUGAR", "WATER");
    }

    private void validateUpdateDto(com.hn.nutricarebe.dto.request.NutritionRuleUpdateDto dto) {
        if (dto.getTargetType() == TargetType.NUTRIENT) {
            String code = safeUpper(dto.getTargetCode());
            if (code == null || !allowedNutrients().contains(code) || dto.getComparator() == null) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT);
            }
            switch (dto.getComparator()) {
                case BETWEEN -> {
                    if (dto.getThresholdMin() == null || dto.getThresholdMax() == null) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                    if (dto.getThresholdMin().compareTo(dto.getThresholdMax()) > 0) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                }
                case EQ -> {
                    if (dto.getThresholdMin() == null || dto.getThresholdMax() == null) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                    if (dto.getThresholdMin().compareTo(dto.getThresholdMax()) != 0) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                }
                case LT, LTE -> {
                    if (dto.getThresholdMax() == null) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                    if (dto.getThresholdMin() != null) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                }
                case GT, GTE -> {
                    if (dto.getThresholdMin() == null) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                    if (dto.getThresholdMax() != null) {
                        throw new AppException(ErrorCode.INVALID_ARGUMENT);
                    }
                }
                default -> throw new AppException(ErrorCode.INVALID_ARGUMENT);
            }
            // perKg nullable -> default false
            if (dto.getPerKg() == null) dto.setPerKg(Boolean.FALSE);
        } else if (dto.getTargetType() == TargetType.FOOD_TAG) {
            if (dto.getComparator() != null ||
                    dto.getThresholdMin() != null ||
                    dto.getThresholdMax() != null ||
                    dto.getTargetCode() != null) {
                throw new AppException(ErrorCode.INVALID_ARGUMENT);
            }
            dto.setPerKg(Boolean.FALSE);
        } else {
            throw new AppException(ErrorCode.INVALID_ARGUMENT);
        }
        if (dto.getAgeMin() != null && dto.getAgeMax() != null &&
                dto.getAgeMin() > dto.getAgeMax()) {
            throw new AppException(ErrorCode.INVALID_ARGUMENT);
        }
    }

}
