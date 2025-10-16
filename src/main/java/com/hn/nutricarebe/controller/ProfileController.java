package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.UpdateRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.service.ProfileService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@RestController
@RequestMapping("/profiles")
public class ProfileController {
    ProfileService profileService;


    @PutMapping("/update")
    public ApiResponse<Void> updateProfile(@Valid @RequestBody UpdateRequest request) {
        profileService.updateProfile(request);
        return ApiResponse.<Void>builder()
                .message("Update profile successfully")
                .build();
    }
}
