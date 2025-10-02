package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;

public interface FoodService {
    public FoodResponse saveFood(FoodCreationRequest request);
    public FoodResponse getById(UUID id);
    public void deleteById(UUID id);
    public Slice<FoodResponse> findByMealSlot(MealSlot mealSlot, Pageable pageable);
    public Slice<FoodResponse> searchByName(String name, Pageable pageable);
    public Slice<FoodResponse> getAll(Pageable pageable);
    public FoodResponse patchUpdate(UUID id, FoodPatchRequest request);
}
