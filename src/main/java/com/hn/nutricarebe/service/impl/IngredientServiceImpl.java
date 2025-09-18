package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.mapper.IngredientMapper;
import com.hn.nutricarebe.repository.IngredientRepository;
import com.hn.nutricarebe.service.IngredientService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IngredientServiceImpl implements IngredientService {
    IngredientRepository ingredientRepository;
    IngredientMapper ingredientMapper;


    @Override
    @Transactional
    public IngredientResponse saveIngredient(IngredientCreationRequest request) {
        Ingredient i = ingredientMapper.toIngredient(request);
        Ingredient savedIngredient = ingredientRepository.save(i);
        return ingredientMapper.toIngredientResponse(savedIngredient);
    }
}
