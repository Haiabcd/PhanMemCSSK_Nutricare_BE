package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.response.PlanLogIngredientResponse;
import com.hn.nutricarebe.entity.PlanLogIngredient;
import org.mapstruct.Context;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",
        uses = {IngredientMapper.class}
)
public interface PlanLogIngredientMapper {
    PlanLogIngredientResponse toPlanLogIngredientResponse(PlanLogIngredient planLogIngredient, @Context CdnHelper cdnHelper);
}
