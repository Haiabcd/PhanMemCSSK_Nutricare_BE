package com.hn.nutricarebe.controller;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.hn.nutricarebe.dto.request.AdminCredentialUpdateRequest;
import com.hn.nutricarebe.dto.request.AdminLoginRequest;
import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.request.RefreshRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.AuthService;
import com.hn.nutricarebe.utils.OAuthExchangeStore;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/auths")
public class AuthController {
    AuthService authService;
    OAuthExchangeStore exchangeStore;

    @PostMapping("/onboarding")
    public ApiResponse<OnboardingResponse> onboarding(@Valid @RequestBody OnboardingRequest request) {
        return ApiResponse.<OnboardingResponse>builder()
                .message("Onboarding thành công")
                .data(authService.onBoarding(request))
                .build();
    }

    // ===========================Google OAuth2================================= //
    @PostMapping("/google/start")
    public ApiResponse<Map<String, String>> googleStart(
            @RequestParam(required = false) String device, @RequestParam(required = false) Boolean upgrade) {
        return ApiResponse.<Map<String, String>>builder()
                .message("Khởi tạo OAuth với Google thành công")
                .data(authService.startGoogleOAuth(device, upgrade))
                .build();
    }

    @GetMapping("/google/callback")
    public ResponseEntity<Void> googleCallback(
            @RequestParam String code,
            @RequestParam("app_state") String appState,
            @RequestParam("device") String device,
            @RequestParam("upgrade") Boolean upgrade,
            @RequestParam(required = false) String error,
            @RequestParam(name = "error_description", required = false) String errorDesc) {
        final String DEFAULT_ERROR = "nutricare://oauth/error";

        try {
            if (error != null) {
                String reason = URLEncoder.encode(errorDesc != null ? errorDesc : error, StandardCharsets.UTF_8);
                URI fail = URI.create(DEFAULT_ERROR + "?reason=" + reason);
                return ResponseEntity.status(HttpStatus.FOUND).location(fail).build();
            }

            GoogleCallbackResponse res = authService.googleCallback(code, appState, device, upgrade);

            String returnTo =
                    switch (res.getOutcome()) {
                        case FIRST_TIME_GOOGLE -> "nutricare://oauth/first";
                        case GUEST_UPGRADE -> "nutricare://oauth/upgrade";
                        case RETURNING_GOOGLE -> {
                            String x = UUID.randomUUID().toString();
                            exchangeStore.put(x, res.getTokenResponse());
                            yield "nutricare://oauth/returning?x=" + URLEncoder.encode(x, StandardCharsets.UTF_8);
                        }
                    };

            URI success = URI.create(returnTo);
            return ResponseEntity.status(HttpStatus.FOUND).location(success).build();

        } catch (AppException ex) {
            String reason;
            if (ex.getErrorCode() == ErrorCode.PROVIDER_ALREADY_LINKED) {
                reason = "Tài khoản Google này đã liên kết với user khác";
            } else {
                reason = "Đăng nhập thất bại";
            }
            URI fail = URI.create(DEFAULT_ERROR + "?reason=" + URLEncoder.encode(reason, StandardCharsets.UTF_8));
            return ResponseEntity.status(HttpStatus.FOUND).location(fail).build();
        }
    }

    @GetMapping("/google/redeem")
    public ApiResponse<TokenPairResponse> redeem(@RequestParam("x") String x) {
        TokenPairResponse tp = exchangeStore.take(x, TokenPairResponse.class);
        return ApiResponse.<TokenPairResponse>builder()
                .message("Lấy token thành công")
                .data(tp)
                .build();
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
        return ApiResponse.<Void>builder().message("Đăng xuất thành công").build();
    }

    @PostMapping("/login")
    ApiResponse<AdminLoginResponse> authenticate(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.<AdminLoginResponse>builder()
                .message("Đăng nhập admin thành công")
                .data(authService.authenticate(request))
                .build();
    }

    @PatchMapping("/change")
    public ApiResponse<Void> updateAdminCredentials(@Valid @RequestBody AdminCredentialUpdateRequest request) {
        authService.updateAdminCredentials(request);
        return ApiResponse.<Void>builder()
                .message("Cập nhật thông tin đăng nhập admin thành công")
                .build();
    }
}
