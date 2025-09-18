package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;

public interface IngredientService {
    public IngredientResponse saveIngredient(IngredientCreationRequest request);
}
