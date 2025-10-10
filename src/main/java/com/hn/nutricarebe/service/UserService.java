package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.UserCreationRequest;
import com.hn.nutricarebe.dto.response.InfoResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;

import java.util.UUID;

public interface UserService {
    User saveOnboarding(String device);
    User getUserById(UUID id);
    User getUserByProvider(String providerId, String device);
    UserCreationResponse saveGG(User user);
    InfoResponse getUserByToken();
}
