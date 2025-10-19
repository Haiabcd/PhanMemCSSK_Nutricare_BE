package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.request.FoodPatchRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.enums.MealSlot;
import com.hn.nutricarebe.service.FoodService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/foods")
public class FoodController {
    FoodService foodService;

    // Tạo món ăn mới
    @PostMapping(value = "/save",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FoodResponse> createFood(@Valid @ModelAttribute FoodCreationRequest request) {
        return ApiResponse.<FoodResponse>builder()
                .message("Tạo món ăn thành công")
                .data(foodService.saveFood(request))
                .build();
    }

    // Lấy thông tin món ăn theo ID
    @GetMapping("/{id}")
    public ApiResponse<FoodResponse> getFoodById(@PathVariable("id") UUID id) {
        return ApiResponse.<FoodResponse>builder()
                .message("Lấy thông tin món ăn thành công")
                .data(foodService.getById(id))
                .build();
    }

    // Xoá món ăn theo ID
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteFood(@PathVariable UUID id) {
        foodService.deleteById(id);
        return ApiResponse.<Void>builder()
                .message("Xoá món ăn thành công")
                .build();
    }

    // Lấy danh sách món ăn theo khung bữa ăn với phân trang
    @GetMapping
    public ApiResponse<Slice<FoodResponse>> listByMealSlot(
            @RequestParam("mealSlot") MealSlot mealSlot,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Slice<FoodResponse> foods = foodService.findByMealSlot(mealSlot, pageable);
        return ApiResponse.<Slice<FoodResponse>>builder()
                .message("Lấy danh sách món ăn thành công")
                .data(foods)
                .build();
    }

    // Tìm kiếm món ăn theo tên với phân trang
    @GetMapping("/search")
    public ApiResponse<Slice<FoodResponse>> searchByName(
            @RequestParam("name") String name,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Slice<FoodResponse> foods = foodService.searchByName(name, pageable);
        return ApiResponse.<Slice<FoodResponse>>builder()
                .message("Tìm kiếm món ăn thành công")
                .data(foods)
                .build();
    }

    // Lấy tất cả món ăn với phân trang
    @GetMapping("/all")
    public ApiResponse<Slice<FoodResponse>> getAll(
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        return ApiResponse.<Slice<FoodResponse>>builder()
                .message("Lấy tất cả món ăn thành công")
                .data(foodService.getAll(pageable))
                .build();
    }

    // Cập nhật một phần thông tin món ăn
    @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FoodResponse> patchUpdate(
            @PathVariable UUID id,
            @Valid @ModelAttribute FoodPatchRequest req
    ) {
        FoodResponse data = foodService.patchUpdate(id, req);
        return ApiResponse.<FoodResponse>builder()
                .message("Cập nhật món ăn thành công")
                .data(data)
                .build();
    }


    @GetMapping("/autocomplete")
    public ApiResponse<List<FoodResponse>> autocomplete(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ApiResponse.<List<FoodResponse>>builder()
                .message("Gợi ý món ăn")
                .data(foodService.autocompleteFoods(keyword, limit))
                .build();
    }
}
