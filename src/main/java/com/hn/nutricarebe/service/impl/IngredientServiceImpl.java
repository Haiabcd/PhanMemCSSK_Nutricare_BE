package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.entity.Ingredient;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.CdnHelper;
import com.hn.nutricarebe.mapper.IngredientMapper;
import com.hn.nutricarebe.repository.IngredientRepository;
import com.hn.nutricarebe.service.IngredientService;
import com.hn.nutricarebe.service.S3Service;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IngredientServiceImpl implements IngredientService {
    IngredientRepository ingredientRepository;
    IngredientMapper ingredientMapper;
    S3Service s3Service;
    CdnHelper cdnHelper;

    // Tạo mới nguyên liệu
    @Override
    @Transactional
    public IngredientResponse saveIngredient(IngredientCreationRequest request) {
        String normalizedName = normalizeName(request.getName());
        if (ingredientRepository.existsByNameIgnoreCase(normalizedName)) {
            throw new AppException(ErrorCode.INGREDIENT_NAME_EXISTED);
        }
        Ingredient i = ingredientMapper.toIngredient(request);
        i.setName(normalizedName);

        String objectKey = null;
        try {
            if (request.getImage() != null && !request.getImage().isEmpty()) {
                objectKey = s3Service.uploadObject(request.getImage(), "images/ingredients");
                i.setImageKey(objectKey);
            }
            Ingredient saved = ingredientRepository.save(i);
            return ingredientMapper.toIngredientResponse(saved, cdnHelper);
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

    // Lấy nguyên liệu theo ID
    @Override
    public IngredientResponse getById(UUID id) {
        Ingredient ingredient = ingredientRepository.findWithCollectionsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INGREDIENT_NOT_FOUND));
        return ingredientMapper.toIngredientResponse(ingredient, cdnHelper);
    }

    // Lấy tất cả nguyên liệu
    private String normalizeName(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("\\s+", " ");
    }

    // Xóa nguyên liệu theo ID
    @Override
    @Transactional
    public void deleteById(UUID id) {
        Ingredient ing = ingredientRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INGREDIENT_NOT_FOUND));
        String key = ing.getImageKey();
        if (key != null && !key.isBlank()) {
            try {
                s3Service.deleteObject(key);
            } catch (RuntimeException e) {
                throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
            }
        }
        try {
            ingredientRepository.deleteById(ing.getId());
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.DELETE_INGREDIENT_CONFLICT);
        }
    }

    // Lấy tất cả nguyên liệu
    @Override
    public Slice<IngredientResponse> getAll(Pageable pageable) {
        Slice<Ingredient> slice = ingredientRepository.findAllBy(pageable);
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(ingredient -> ingredientMapper.toIngredientResponse(ingredient, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }

    // Tìm kiếm nguyên liệu theo tên gần đúng
    @Override
    public Slice<IngredientResponse> searchByName(String q, Pageable pageable) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.length() < 2) {
            throw new AppException(ErrorCode.NAME_EMPTY);
        }

        Slice<Ingredient> slice = ingredientRepository.findByNameContainingIgnoreCase(keyword, pageable);
        return new SliceImpl<>(
                slice.getContent().stream()
                        .map(ingredient -> ingredientMapper.toIngredientResponse(ingredient, cdnHelper))
                        .toList(),
                pageable,
                slice.hasNext()
        );
    }


    @Override
    @Transactional(readOnly = true)
    public List<IngredientResponse> autocompleteIngredients(String keyword, int limit) {
        int size = Math.max(1, Math.min(limit, 20));

        Pageable pageable = PageRequest.of(0, size);

        return ingredientRepository
                .searchByNameOrAlias(keyword.trim(), pageable)
                .getContent()
                .stream()
                .map(i -> ingredientMapper.toIngredientResponse(i, cdnHelper))
                .toList();
    }
    }

