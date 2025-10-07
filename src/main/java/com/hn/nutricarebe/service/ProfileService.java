package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.User;

import java.util.UUID;

public interface ProfileService {
    public ProfileCreationResponse save(ProfileCreationRequest request, User user);
    public void updateAvatarAndName(String avatarUrl, String name, UUID userId);
}
