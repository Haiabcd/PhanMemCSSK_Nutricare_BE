package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.NutritionRequest;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.entity.Nutrition;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NutritionMapper {
    Nutrition toNutrition(NutritionRequest request);
    NutritionResponse toNutritionResponse(Nutrition nutrition);

}
