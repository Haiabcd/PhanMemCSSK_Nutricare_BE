package com.hn.nutricarebe.mapper;


import com.hn.nutricarebe.dto.request.ConditionRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.dto.response.UserConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.NutritionRule;
import org.mapstruct.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {NutritionRuleMapper.class})
public interface ConditionMapper {
    Condition toCondition(ConditionRequest request);
    @Mapping(target = "nutritionRules", ignore = true)
    ConditionResponse toConditionResponse(Condition condition);

    @Mapping(target = "nutritionRules", ignore = true)
    ConditionResponse toConditionResponse(Condition condition, @Context Map<UUID, List<NutritionRule>> rulesByCondition);

    UserConditionResponse toUserConditionResponse(Condition condition);

    @AfterMapping
    default void fillRules(Condition source,
                           @MappingTarget ConditionResponse target,
                           @Context Map<UUID, List<NutritionRule>> rulesByCondition,
                           NutritionRuleMapper ruleMapper) {
        var rules = rulesByCondition.getOrDefault(source.getId(), List.of());
        target.setNutritionRules(ruleMapper.toResponses(rules));
    }

    @AfterMapping
    default void ensureRulesNotNull(@MappingTarget ConditionResponse target) {
        if (target.getNutritionRules() == null) {
            target.setNutritionRules(List.of());
        }
    }
}
