package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.repository.IngredientRepository;
import com.hn.nutricarebe.service.IngredientService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IngredientServiceImpl implements IngredientService {
    IngredientRepository ingredientRepository;


    @Override
    public IngredientResponse saveIngredient(IngredientCreationRequest request) {
        return null;
    }
}
