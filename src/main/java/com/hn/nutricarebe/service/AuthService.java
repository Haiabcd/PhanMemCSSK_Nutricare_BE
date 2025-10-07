package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.LoginProviderResponse;
import com.hn.nutricarebe.dto.response.OnboardingResponse;


import java.util.Map;


public interface AuthService {
    public OnboardingResponse onBoarding(OnboardingRequest request);
    public Map<String, String> startGoogleOAuth(String device);
    public LoginProviderResponse googleCallback(String code, String state, String device);
}
