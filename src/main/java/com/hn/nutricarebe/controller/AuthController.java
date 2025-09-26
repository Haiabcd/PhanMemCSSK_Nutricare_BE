package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.service.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
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


    // ===========================Google OAuth2================================= //
    @PostMapping("/google/start")
    public ApiResponse<Map<String, String>> googleStart() {
        return ApiResponse.<Map<String, String>>builder()
                .message("Khởi tạo OAuth với Google thành công")
                .data(authService.startGoogleOAuth())
                .build();
    }

    @GetMapping("/google/callback")
    public ApiResponse<LoginResponse> googleCallback(
            @RequestParam String code,
            @RequestParam("app_state") String appState,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDesc
    ) {
        if (error != null) {
            return ApiResponse.<LoginResponse>builder()
                    .code(4000)
                    .message("OAuth error: " + error)
                    .errors(Map.of("supabase", List.of(errorDesc != null ? errorDesc : "unknown")))
                    .build();
        }
        return ApiResponse.<LoginResponse>builder()
                .message("Đăng nhập GOOGLE thành công")
                .data(authService.googleCallback(code, appState))
                .build();
    }
    // ===========================Google OAuth2================================= //

}
