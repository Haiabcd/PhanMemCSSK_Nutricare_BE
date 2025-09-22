package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.entity.Ingredient;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = { NutritionMapper.class })
public interface IngredientMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "recipeIngredients", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Ingredient toIngredient(IngredientCreationRequest req);


    @Mapping(target = "imageUrl", expression = "java(cdnHelper.buildUrl(ingredient.getImageKey()))")
    IngredientResponse toIngredientResponse(Ingredient ingredient, @Context CdnHelper cdnHelper);


}
