package com.hn.nutricarebe.service;

import java.util.Map;
import java.util.UUID;

import com.hn.nutricarebe.dto.ai.ProfileAI;
import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.request.UpdateRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.User;

public interface ProfileService {
    ProfileCreationResponse save(ProfileCreationRequest request, User user);

    ProfileCreationResponse findByUserId(UUID userId);

    void updateProfile(UpdateRequest request);

    ProfileCreationRequest findByUserId_request(UUID userId);

    ProfileAI getHealthProfile(UUID userId);

    Map<String, Long> getGoalStats();
}
