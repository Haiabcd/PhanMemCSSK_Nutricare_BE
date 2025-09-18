package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.UserCreationRequest;
import com.hn.nutricarebe.dto.response.UserCreationResponse;

import java.util.UUID;

public interface UserService {
    public UserCreationResponse save(UserCreationRequest request);
    public UserCreationResponse getUserById(UUID id);
}
