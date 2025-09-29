package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;

import java.util.UUID;

public interface IngredientService {
    public IngredientResponse saveIngredient(IngredientCreationRequest request);
    public IngredientResponse getById(UUID id);
    public void deleteById(UUID id);
}
