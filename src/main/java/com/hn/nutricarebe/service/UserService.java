package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.UserCreationRequest;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;

import java.util.UUID;

public interface UserService {
    public User saveOnboarding(UserCreationRequest request);
    public UserCreationResponse getUserById(UUID id);
    public User getUserByProvider(String providerId, String device);
    public UserCreationResponse saveGG(User user);
}
