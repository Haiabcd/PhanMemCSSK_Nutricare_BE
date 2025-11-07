package com.hn.nutricarebe.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.request.IngredientUpdateRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.service.IngredientService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/ingredients")
public class IngredientController {

    IngredientService ingredientService;

    // Tạo nguyên liệu mới
    @PostMapping("/save")
    public ApiResponse<Void> saveIngredient(@Valid @ModelAttribute IngredientCreationRequest request) {
        ingredientService.saveIngredient(request);
        return ApiResponse.<Void>builder().message("Tạo nguyên liệu thành công").build();
    }

    // Lấy nguyên liệu theo ID
    @GetMapping("/{id}")
    public ApiResponse<IngredientResponse> getIngredientById(@PathVariable UUID id) {
        return ApiResponse.<IngredientResponse>builder()
                .message("Lấy thông tin nguyên liệu thành công")
                .data(ingredientService.getById(id))
                .build();
    }

    // Xoá nguyên liệu theo ID
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteIngredient(@PathVariable UUID id) {
        ingredientService.deleteById(id);
        return ApiResponse.<Void>builder().message("Xoá nguyên liệu thành công").build();
    }

    // Lấy tất cả nguyên liệu
    @GetMapping("/all")
    public ApiResponse<Slice<IngredientResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.<Slice<IngredientResponse>>builder()
                .message("Lấy danh sách nguyên liệu thành công")
                .data(ingredientService.getAll(pageable))
                .build();
    }

    // Gợi ý nguyên liệu
    @GetMapping("/autocomplete")
    public ApiResponse<List<IngredientResponse>> autocomplete(
            @RequestParam String keyword, @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.<List<IngredientResponse>>builder()
                .message("Gợi ý món ăn")
                .data(ingredientService.autocompleteIngredients(keyword, limit))
                .build();
    }

    // Cập nhật nguyên liệu
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> updateIngredient(
            @PathVariable UUID id, @Valid @ModelAttribute IngredientUpdateRequest request) {
        ingredientService.updateIngredient(id, request);
        return ApiResponse.<Void>builder()
                .message("Cập nhật nguyên liệu thành công")
                .build();
    }
}
