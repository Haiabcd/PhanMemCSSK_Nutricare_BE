package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.UserCreationRequest;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;
import com.hn.nutricarebe.mapper.UserMapper;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.UserService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;

    @Override
    public UserCreationResponse save(UserCreationRequest request) {
       if(userRepository.existsByDeviceId(request.getDeviceId())){
           throw new RuntimeException("User with deviceId " + request.getDeviceId() + " already exists");
       }

        User userRequest = userMapper.toUser(request);
        userRequest.setStatus(UserStatus.ACTIVE);
        if(request.getProvider().equals(Provider.NONE)){
            userRequest.setRole(Role.GUEST);
        }
        User u = userRepository.save(userRequest);
        return userMapper.toUserCreationResponse(u);
    }

    @Override
    public UserCreationResponse getUserById(UUID id) {
        User u =  userRepository.findById(id).
                orElseThrow(() -> new RuntimeException("User with id " + id + " not found"));
        return userMapper.toUserCreationResponse(u);
    }
}
