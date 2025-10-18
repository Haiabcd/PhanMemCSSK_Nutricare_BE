package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.UUID;

public interface IngredientService {
    public IngredientResponse saveIngredient(IngredientCreationRequest request);
    public IngredientResponse getById(UUID id);
    public void deleteById(UUID id);
    public Slice<IngredientResponse> getAll(Pageable pageable);
    public Slice<IngredientResponse> searchByName(String q, Pageable pageable);
    List<IngredientResponse> autocompleteIngredients(String keyword, int limit);
}
