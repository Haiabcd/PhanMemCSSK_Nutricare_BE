package com.hn.nutricarebe.mapper;


import com.hn.nutricarebe.dto.request.AllergyRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.NutritionRule;
import org.mapstruct.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring", uses = {NutritionRuleMapper.class})
public interface  AllergyMapper {
    Allergy toAllergy(AllergyRequest request);

    @Mapping(target = "nutritionRules", ignore = true)
    AllergyResponse toAllergyResponse(Allergy allergy,
                                      @Context Map<UUID, List<NutritionRule>> rulesByAllergy,
                                      @Context NutritionRuleMapper ruleMapper);

    UserAllergyResponse toUserAllergyResponse(Allergy allergy);

    @Mapping(target = "nutritionRules", ignore = true)
    AllergyResponse toAllergyResponse(Allergy allergy);

    @AfterMapping
    default void fillRules(Allergy source,
                           @MappingTarget AllergyResponse target,
                           @Context Map<UUID, List<NutritionRule>> rulesByAllergy,
                           @Context NutritionRuleMapper ruleMapper) {
        var rules = rulesByAllergy.getOrDefault(source.getId(), List.of());
        target.setNutritionRules(ruleMapper.toResponses(rules));
    }

    @AfterMapping
    default void fillRulesBuilder(
            Allergy source,
            @MappingTarget AllergyResponse.AllergyResponseBuilder target,
            @Context Map<UUID, List<NutritionRule>> rulesByAllergy,
            @Context NutritionRuleMapper ruleMapper
    ) {
        var rules = rulesByAllergy.getOrDefault(source.getId(), List.of());
        target.nutritionRules(ruleMapper.toResponses(rules));
    }

    @AfterMapping
    default void ensureRulesNotNull(@MappingTarget AllergyResponse target) {
        if (target.getNutritionRules() == null) {
            target.setNutritionRules(List.of());
        }
    }
}
