package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.FoodMapper;
import com.hn.nutricarebe.mapper.UserResolver;
import com.hn.nutricarebe.repository.FoodRepository;
import com.hn.nutricarebe.service.FoodService;
import com.hn.nutricarebe.service.S3Service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FoodServiceImpl implements FoodService {
    FoodRepository foodRepository;
    FoodMapper foodMapper;
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
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Tìm món ăn theo tên 
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

    @Override
    @Transactional
    public FoodResponse patchUpdate(UUID id, FoodPatchRequest req) {
        Food food = foodRepository.findWithCollectionsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));

        // 2) Đổi tên nếu request có name
        String newName = normalizeName(req.getName());
        if(newName != null && !newName.equalsIgnoreCase(food.getName())
                && !foodRepository.existsByNameIgnoreCase(newName)) {
            food.setName(newName);
        }
        // 3) Ảnh: nếu có file mới -> upload trước, set key mới lên entity
        String oldKey = food.getImageKey();
        String newKey = null;
        MultipartFile file = req.getImage();
        if (file != null && !file.isEmpty()) {
            try {
                newKey = s3Service.uploadObject(file, "images/foods");
                if (newKey != null && !newKey.isBlank()) {
                   food.setImageKey(newKey);
                }
            } catch (IOException e) {
                throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }
        // 4) Patch các field còn lại (null sẽ bị bỏ qua)
        foodMapper.patch(food, req);
        // 5) Lưu DB
        Food saved = foodRepository.save(food);
        // 6) Nếu có ảnh mới -> sau khi DB ok, thử xóa ảnh cũ (không rollback nếu xóa fail)
        if (newKey != null && oldKey != null && !oldKey.isBlank() && !oldKey.equals(newKey)) {
            try {
                s3Service.deleteObject(oldKey);
            } catch (Exception ex) {
               throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
            }
        }
        return foodMapper.toFoodResponse(saved, cdnHelper);
    }


    @Override
    @Transactional(readOnly = true)
    public List<FoodResponse> autocompleteFoods(String keyword, int limit) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }
        Pageable pageable = PageRequest.of(0, Math.min(limit, 20));
        return  foodRepository.searchByNameUnaccent(keyword.trim(), pageable)
                .getContent()
                .stream()
                .map(food -> foodMapper.toFoodResponse(food, cdnHelper))
                .toList();
    }


    // Chuẩn hoá tên: loại bỏ khoảng trắng thừa
    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

}
