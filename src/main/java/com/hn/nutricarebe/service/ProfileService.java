package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;

public interface ProfileService {
    public ProfileCreationResponse save(ProfileCreationRequest request);
}
