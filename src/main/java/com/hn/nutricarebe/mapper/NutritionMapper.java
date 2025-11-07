package com.hn.nutricarebe.mapper;

import org.mapstruct.Mapper;

import com.hn.nutricarebe.dto.request.NutritionRequest;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.Nutrition;

@Mapper(componentModel = "spring")
public interface NutritionMapper {
    Nutrition toNutrition(NutritionRequest request);

    NutritionResponse toNutritionResponse(Nutrition nutrition);
}
