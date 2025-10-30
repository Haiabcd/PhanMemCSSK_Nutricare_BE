package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.AdminLoginRequest;
import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.AdminLoginResponse;
import com.hn.nutricarebe.dto.response.GoogleCallbackResponse;
import com.hn.nutricarebe.dto.response.OnboardingResponse;
import com.hn.nutricarebe.dto.response.TokenPairResponse;
import java.util.Map;



public interface AuthService {
    OnboardingResponse onBoarding(OnboardingRequest request);
    Map<String, String> startGoogleOAuth(String device,Boolean upgrade);
    GoogleCallbackResponse googleCallback(String code, String state, String device, Boolean upgrade);
    TokenPairResponse refresh(String refreshTokenRaw);
    void logout(String refreshTokenRaw);
    AdminLoginResponse authenticate(AdminLoginRequest request);
}
