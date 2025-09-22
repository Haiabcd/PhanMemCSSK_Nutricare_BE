package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.IngredientCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.IngredientResponse;
import com.hn.nutricarebe.service.IngredientService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/ingredients")
public class IngredientController {

    IngredientService ingredientService;


    @PostMapping("/save")
    public ApiResponse<IngredientResponse> saveIngredient(@Valid @ModelAttribute IngredientCreationRequest request) {
        return ApiResponse.<IngredientResponse>builder()
                .message("Tạo nguyên liệu thành công")
                .data(ingredientService.saveIngredient(request))
                .build();
    }
}
