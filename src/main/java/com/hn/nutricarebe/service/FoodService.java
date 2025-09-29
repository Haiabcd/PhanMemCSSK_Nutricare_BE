package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import java.util.UUID;

public interface FoodService {
    public FoodResponse saveFood(FoodCreationRequest request);
    public FoodResponse getById(UUID id);
}
