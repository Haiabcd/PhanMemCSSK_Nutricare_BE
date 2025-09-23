package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodSliceResponse;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
        Food food = foodMapper.toFood(request);
        food.setName(normalizedName);
        food.setCreatedBy(userResolver.getUserByToken());

        String objectKey = null;
        try {
            if (request.getImage() != null && !request.getImage().isEmpty()) {
                objectKey = s3Service.uploadObject(request.getImage(), "images/foods");
                food.setImageKey(objectKey);
            }
            Food saved = foodRepository.save(food);
            return foodMapper.toFoodResponse(saved, cdnHelper);
        } catch (DataIntegrityViolationException e) {
            if (objectKey != null) s3Service.deleteObject(objectKey);
            throw e;
        } catch (IOException e) {
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        } catch (RuntimeException e) {
            if (objectKey != null) s3Service.deleteObject(objectKey);
            throw e;
        }
    }



    @Override
    @Transactional(readOnly = true)
    public FoodSliceResponse getFoodList(Integer size, UUID cursorId, Instant cursorCreatedAt) {
        //Mặc định trả về 20, tối đa 50
        int limit = (size == null || size <= 0 || size > 50) ? 20 : size;

        List<Food> foods;
        if (cursorId == null || cursorCreatedAt == null) {
            // Trang đầu
            foods = foodRepository.findFirstPage(PageRequest.of(0, limit + 1));
        } else {
            // Trang tiếp
            foods = foodRepository.findNextPage(cursorCreatedAt, cursorId, PageRequest.of(0, limit + 1));
        }
        boolean hasNext = foods.size() > limit;
        if (hasNext) {
            foods = foods.subList(0, limit);
        }
        List<FoodResponse> items = foods.stream()
                .map(f -> foodMapper.toFoodResponse(f, cdnHelper))
                .toList();

        UUID nextCursorId = hasNext ? foods.get(foods.size() - 1).getId() : null;
        Instant nextCursorCreatedAt = hasNext ? foods.get(foods.size() - 1).getCreatedAt() : null;

        return FoodSliceResponse.builder()
                .items(items)
                .nextCursorId(nextCursorId)
                .nextCursorCreatedAt(nextCursorCreatedAt)
                .hasNext(hasNext)
                .build();
    }


    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

}
