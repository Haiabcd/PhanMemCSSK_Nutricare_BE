package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.request.RefreshRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.service.AuthService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    public ApiResponse<Map<String, String>> googleStart(@RequestParam(required = false) String device) {
        return ApiResponse.<Map<String, String>>builder()
                .message("Khởi tạo OAuth với Google thành công")
                .data(authService.startGoogleOAuth(device))
                .build();
    }

//    @GetMapping("/google/callback")
//    public ApiResponse<LoginProviderResponse> googleCallback(
//            @RequestParam String code, // Mã xác thực từ Google trả về
//            @RequestParam("app_state") String appState,
//            @RequestParam("device") String device,
//            @RequestParam(required = false) String error,  // Lỗi (nếu có)
//            @RequestParam(name = "error_description", required = false) String errorDesc // Mô tả lỗi
//    ) {
//        if (error != null) {
//            return ApiResponse.<LoginProviderResponse>builder()
//                    .code(4000)
//                    .message("OAuth error: " + error)
//                    .errors(Map.of("supabase", List.of(errorDesc != null ? errorDesc : "unknown")))
//                    .build();
//        }
//        return ApiResponse.<LoginProviderResponse>builder()
//                .message("Đăng nhập GOOGLE thành công")
//                .data(authService.googleCallback(code, appState, device))
//                .build();
//    }



    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam String code,
            @RequestParam("app_state") String appState,
            @RequestParam("device") String device,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDesc,
            @RequestParam(name = "return_to", required = false) String returnTo
    ) {
        final String DEFAULT_SUCCESS = "nutricare://oauth/success";
        final String DEFAULT_ERROR   = "nutricare://oauth/error";

        // Chống open-redirect: chỉ cho phép scheme của app
        if (returnTo == null || returnTo.isBlank() || !returnTo.startsWith("nutricare://")) {
            returnTo = DEFAULT_SUCCESS;
        }

        if (error != null) {
            String reason = URLEncoder.encode(errorDesc != null ? errorDesc : error, StandardCharsets.UTF_8);
            URI fail = URI.create(DEFAULT_ERROR + "?reason=" + reason);
            return ResponseEntity.status(HttpStatus.FOUND).location(fail).build();
        }
        // Gọi service để hoàn tất login (tạo user, lưu token refresh vào DB, v.v.)
        authService.googleCallback(code, appState, device);

        // Thành công: 302 về deep link (không trả body JSON)
        URI success = URI.create(returnTo);
        return ResponseEntity.status(HttpStatus.FOUND).location(success).build();
    }

    // ===========================Google OAuth2================================= //

    @PostMapping("/refresh")
    public ApiResponse<TokenPairResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        var tokens = authService.refresh(req.getRefreshToken());
        return ApiResponse.<TokenPairResponse>builder()
                .message("Làm mới token thành công")
                .data(tokens)
                .build();
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return ApiResponse.<Void>builder()
                .message("Đăng xuất thành công")
                .build();
    }


}
