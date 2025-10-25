package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.response.HeaderResponse;
import com.hn.nutricarebe.dto.response.InfoResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;

import java.util.UUID;

public interface UserService {
    User saveOnboarding(String device);
    User getUserById(UUID id);
    UserCreationResponse saveGG(User user);
    InfoResponse getUserByToken();
    HeaderResponse getHeader();
}
