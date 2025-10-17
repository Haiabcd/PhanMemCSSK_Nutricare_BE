package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.request.UpdateRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.User;

import java.util.UUID;

public interface ProfileService {
    ProfileCreationResponse save(ProfileCreationRequest request, User user);
    ProfileCreationResponse findByUserId(UUID userId);
    void updateProfile(UpdateRequest request);
    ProfileCreationRequest findByUserId_request(UUID userId);
}
