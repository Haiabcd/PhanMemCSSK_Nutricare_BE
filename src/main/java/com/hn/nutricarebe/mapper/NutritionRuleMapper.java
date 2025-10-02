package com.hn.nutricarebe.mapper;



import com.hn.nutricarebe.dto.request.NutritionRuleCreationRequest;
import com.hn.nutricarebe.entity.NutritionRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NutritionRuleMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "condition", ignore = true)  // sẽ set ở service
    @Mapping(target = "allergy", ignore = true)    // sẽ set ở service
    @Mapping(target = "createAt", ignore = true)
    @Mapping(target = "updateAt", ignore = true)
    NutritionRule toNutritionRule(NutritionRuleCreationRequest req);
}
