package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.service.ProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/profiles")
public class ProfileController {
    ProfileService profileService;

//    @PostMapping("/save")
//    public ApiResponse<ProfileCreationResponse> save(@Valid @RequestBody ProfileCreationRequest request, BindingResult result) {
//        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
//        if (result.hasErrors()) {
//            result.getFieldErrors().forEach(e ->
//                    errors.put(e.getField(), e.getDefaultMessage()));
//            return ApiResponse.<ProfileCreationResponse>builder()
//                    .code(1000)
//                    .message("Lỗi dữ liệu đầu vào")
//                    .errors(errors)
//                    .build();
//        }
//        return ApiResponse.<ProfileCreationResponse>builder()
//                .message("Tạo hồ sơ thành công")
//                .data(profileService.save(request))
//                .build();
//    }
}
