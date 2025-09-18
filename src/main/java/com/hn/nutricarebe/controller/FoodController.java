package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.FoodCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.FoodResponse;
import com.hn.nutricarebe.service.FoodService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/foods")
public class FoodController {
    FoodService foodService;


    @PostMapping("/save")
    public ApiResponse<FoodResponse> save(@Valid @RequestBody FoodCreationRequest request){
        return ApiResponse.<FoodResponse>builder()
                .message("Tạo thực phẩm thành công")
                .data(foodService.saveFood(request))
                .build();
    }

}
