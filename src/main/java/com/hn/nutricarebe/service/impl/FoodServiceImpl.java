package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.MealSlot;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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

    // Tạo món ăn mới
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

    // Lấy thông tin món ăn theo ID
    @Override
    public FoodResponse getById(UUID id) {
        Food food = foodRepository.findWithCollectionsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));
        return foodMapper.toFoodResponse(food, cdnHelper);
    }

    // Xoá món ăn theo ID
    @Override
    @Transactional
    public void deleteById(UUID id) {
        Food food = foodRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));
        String key = food.getImageKey();
        if (key != null && !key.isBlank()) {
            try {
                s3Service.deleteObject(key);
            } catch (RuntimeException e) {
                throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
            }
        }

        try {
            foodRepository.delete(food);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.DELETE_CONFLICT);
        }
    }

    // Tìm món ăn theo khung bữa ăn (MealSlot) với phân trang
    @Override
    public Slice<FoodResponse> findByMealSlot(MealSlot mealSlot, Pageable pageable) {
        Slice<Food> slice = foodRepository.findByMealSlot(mealSlot, pageable);
        // map entity → dto, giữ nguyên hasNext
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Tìm món ăn theo tên với phân trang (Tìm gần đúng)
    @Override
    public Slice<FoodResponse> searchByName(String name, Pageable pageable) {
        String keyword = (name == null) ? "" : name.trim();
        if (keyword.length() < 2) {
            throw new AppException(ErrorCode.NAME_EMPTY);
        }

         Slice<Food> slice = foodRepository.findByNameContainingIgnoreCase(keyword, pageable);

        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Lấy tất cả món ăn với phân trang
    @Override
    public Slice<FoodResponse> getAll(Pageable pageable) {
        Slice<Food> slice = foodRepository.findAllBy(pageable);
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Chuẩn hoá tên: loại bỏ khoảng trắng thừa
    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

}
