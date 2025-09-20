package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.FoodMaper;
import com.hn.nutricarebe.mapper.UserResolver;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.service.FoodService;
import com.hn.nutricarebe.service.S3Service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FoodServiceImpl implements FoodService {
    FoodRepository foodRepository;
    FoodMaper foodMapper;
    UserResolver userResolver;
    S3Service s3Service;
    CdnHelper cdnHelper;

    @Override
    @Transactional
    public FoodResponse saveFood(FoodCreationRequest request) {
        String normalizedName = normalizeName(request.getName());
        if (foodRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(ErrorCode.FOOD_NAME_EXISTED);
        }
        Food food = foodMapper.toFood(request, userResolver);
        food.setName(normalizedName);

        if (request.getImage() != null && !request.getImage().isEmpty()) {
            try {
                String objectKey = s3Service.uploadObject(request.getImage(), "images/foods");
                food.setImageKey(objectKey);
            } catch (IOException e) {
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }

        try {
            Food saved = foodRepository.save(food);
            return foodMapper.toFoodResponse(saved, cdnHelper);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.FOOD_NAME_EXISTED);
        }
    }

    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

}
