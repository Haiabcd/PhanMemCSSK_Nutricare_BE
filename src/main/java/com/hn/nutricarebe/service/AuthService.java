package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.LoginProviderResponse;
import com.hn.nutricarebe.dto.response.OnboardingResponse;
import com.hn.nutricarebe.dto.response.TokenPairResponse;
import java.util.Map;



public interface AuthService {
    OnboardingResponse onBoarding(OnboardingRequest request);
    Map<String, String> startGoogleOAuth(String device);
    LoginProviderResponse googleCallback(String code, String state, String device);
    TokenPairResponse refresh(String refreshTokenRaw);
    void logout(String refreshTokenRaw);
}
