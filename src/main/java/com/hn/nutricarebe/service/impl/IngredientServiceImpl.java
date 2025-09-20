package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.IngredientMapper;
import com.hn.nutricarebe.repository.IngredientRepository;
import com.hn.nutricarebe.service.IngredientService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
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
        String normalizedName = normalizeName(request.getName());
        if (ingredientRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(ErrorCode.INGREDIENT_NAME_EXISTED);
        }
        Ingredient i = ingredientMapper.toIngredient(request);
        i.setName(normalizedName);
        try {
            Ingredient saved = ingredientRepository.save(i);
            return ingredientMapper.toIngredientResponse(saved);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.INGREDIENT_NAME_EXISTED);
        }
    }

    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }
}
