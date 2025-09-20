package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.AllergyCreationRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.service.AllergyService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/allergies")
public class AllergyController {
    AllergyService allergyService;

    @GetMapping()
    public ApiResponse<List<Allergy>> getAllAllergy() {
        return ApiResponse.<List<Allergy>>builder()
                .message("Lấy danh sách dị ứng thành công")
                .data(allergyService.findAll())
                .build();
    }

    @PostMapping("/save")
    public ApiResponse<AllergyResponse> saveAllergy(@Valid @RequestBody AllergyCreationRequest request) {
        return ApiResponse.<AllergyResponse>builder()
                .message("Tạo dị ứng thành công")
                .data(allergyService.save(request))
                .build();

    }

}
