package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;

public interface FoodService {
    public FoodResponse saveFood(FoodCreationRequest request);
}
