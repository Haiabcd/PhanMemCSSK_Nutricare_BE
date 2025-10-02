package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanItemCreationRequest;
import com.hn.nutricarebe.dto.response.MealPlanItemResponse;
import com.hn.nutricarebe.repository.MealPlanItemRepository;
import com.hn.nutricarebe.service.MealPlanItemService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MealPlanItemServiceImpl implements MealPlanItemService {

    MealPlanItemRepository mealPlanItemRepository;


    @Override
    public MealPlanItemResponse createMealPlanItems(MealPlanItemCreationRequest request) {
        return null;
    }
}
