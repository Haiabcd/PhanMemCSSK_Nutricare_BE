package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodSliceResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import java.time.Instant;
import java.util.UUID;

public interface FoodService {
    public FoodResponse saveFood(FoodCreationRequest request);
}
