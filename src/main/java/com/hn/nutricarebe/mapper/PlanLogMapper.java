package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.response.LogResponse;
import com.hn.nutricarebe.entity.PlanLog;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = { NutritionMapper.class })
public interface PlanLogMapper {
    LogResponse toFoodLogResponse(PlanLog foodLog);
}
