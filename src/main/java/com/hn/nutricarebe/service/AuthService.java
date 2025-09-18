package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.OnboardingResponse;
import org.springframework.stereotype.Service;


public interface AuthService {
    public OnboardingResponse onBoarding(OnboardingRequest request);
}
