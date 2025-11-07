package com.hn.nutricarebe.mapper;

import org.mapstruct.Context;
import org.mapstruct.Mapper;

import com.hn.nutricarebe.dto.response.LogResponse;
import com.hn.nutricarebe.entity.PlanLog;

@Mapper(
        componentModel = "spring",
        uses = {NutritionMapper.class, PlanLogIngredientMapper.class, FoodMapper.class})
public interface PlanLogMapper {
    LogResponse toLogResponse(PlanLog foodLog, @Context CdnHelper cdnHelper);
}
