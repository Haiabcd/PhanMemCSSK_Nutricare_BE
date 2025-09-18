package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.mapper.FoodMaper;
import com.hn.nutricarebe.mapper.UserResolver;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.service.FoodService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FoodServiceImpl implements FoodService {
    FoodRepository foodRepository;
    FoodMaper foodMapper;
    UserResolver userResolver;


    @Override
    @Transactional
    public FoodResponse saveFood(FoodCreationRequest request) {
        Food food = foodMapper.toFood(request, userResolver);
        return foodMapper.toFoodResponse(foodRepository.save(food));
    }
}
