package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.ApiResponse;
import com.hn.nutricarebe.dto.response.OnboardingResponse;
import com.hn.nutricarebe.service.AuthService;
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
@RequestMapping("/auths")
public class AuthController {
    AuthService authService;

    @PostMapping("/onboarding")
    public ApiResponse<OnboardingResponse> onboarding(@Valid  @RequestBody OnboardingRequest request){
        return ApiResponse.<OnboardingResponse>builder()
                .message("Onboarding thành công")
                .data(authService.onBoarding(request))
                .build();
    }
}
