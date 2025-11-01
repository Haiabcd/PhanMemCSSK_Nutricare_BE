package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.ai.DishVisionResult;
import com.hn.nutricarebe.dto.ai.NutritionAudit;
import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.request.IngredientUpdateRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.List;
import java.util.UUID;

public interface IngredientService {
    void saveIngredient(IngredientCreationRequest request);
    IngredientResponse getById(UUID id);
    void deleteById(UUID id);
    Slice<IngredientResponse> getAll(Pageable pageable);
    Slice<IngredientResponse> searchByName(String q, Pageable pageable);
    List<IngredientResponse> autocompleteIngredients(String keyword, int limit);
    NutritionAudit audit(DishVisionResult vision, boolean strict);
    long countIngredients();
    long countNewIngredientsThisWeek();
    void updateIngredient(UUID id, IngredientUpdateRequest request);
}
