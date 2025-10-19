package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.UUID;

public interface FoodService {
    FoodResponse saveFood(FoodCreationRequest request);
    FoodResponse getById(UUID id);
    void deleteById(UUID id);
    Slice<FoodResponse> findByMealSlot(MealSlot mealSlot, Pageable pageable);
    Slice<FoodResponse> searchByName(String name, Pageable pageable);
    Slice<FoodResponse> getAll(Pageable pageable);
    FoodResponse patchUpdate(UUID id, FoodPatchRequest request);
    List<FoodResponse> autocompleteFoods(String keyword, int limit);
}
