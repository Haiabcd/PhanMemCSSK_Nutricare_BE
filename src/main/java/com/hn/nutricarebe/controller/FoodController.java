package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.service.FoodService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/foods")
public class FoodController {
    FoodService foodService;

    @PostMapping(value = "/save",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<FoodResponse> createFood(@Valid @ModelAttribute FoodCreationRequest request) {
        return ApiResponse.<FoodResponse>builder()
                .message("Tạo món ăn thành công")
                .data(foodService.saveFood(request))
                .build();
    }

}
