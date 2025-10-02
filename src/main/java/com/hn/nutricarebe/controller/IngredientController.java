package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.service.IngredientService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/ingredients")
public class IngredientController {

    IngredientService ingredientService;

    // Tạo nguyên liệu mới
    @PostMapping("/save")
    public ApiResponse<IngredientResponse> saveIngredient(@Valid @ModelAttribute IngredientCreationRequest request) {
        return ApiResponse.<IngredientResponse>builder()
                .message("Tạo nguyên liệu thành công")
                .data(ingredientService.saveIngredient(request))
                .build();
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
        return ApiResponse.<Void>builder()
                .message("Xoá nguyên liệu thành công")
                .build();
    }

    // Lấy tất cả nguyên liệu
    @GetMapping("/all")
    public ApiResponse<Slice<IngredientResponse>> getAll(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.<Slice<IngredientResponse>>builder()
                .message("Lấy danh sách nguyên liệu thành công")
                .data(ingredientService.getAll(pageable))
                .build();
    }

    // Tìm kiếm nguyên liệu theo tên
    @GetMapping("/search")
    public ApiResponse<Slice<IngredientResponse>> search(
            @RequestParam("name") String name,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Slice<IngredientResponse> ing = ingredientService.searchByName(name, pageable);
        return ApiResponse.<Slice<IngredientResponse>>builder()
                .message("Tìm kiếm nguyên liệu thành công")
                .data(ing)
                .build();
    }
}
