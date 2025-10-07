package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.UserCreationRequest;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
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
    public User saveOnboarding(UserCreationRequest request) {
        if(userRepository.existsByDeviceId(request.getDeviceId())){
            throw new AppException(ErrorCode.DEVICE_ID_EXISTED);
        }
        User user =  userMapper.toUser(request);
        user.setRole(Role.GUEST);
        user.setProvider(Provider.NONE);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    @Override
    public UserCreationResponse getUserById(UUID id) {
        User u =  userRepository.findById(id).
                orElseThrow(() -> new RuntimeException("User with id " + id + " not found"));
        return userMapper.toUserCreationResponse(u);
    }

    @Override
    public UserCreationResponse saveGG(User user) {
        User saved = userRepository.save(user);
        return userMapper.toUserCreationResponse(saved);
    }

    @Override
    public User getUserByProvider(String providerId, String device) {
        // Kiểm tra đã liên kết gg chưa
        User user = userRepository.findByProviderUserId(providerId).orElse(null);

        // Nếu vẫn chưa, thử theo device
        if (user == null && device != null && !device.isBlank()) {
            user = userRepository.findByDeviceId(device).orElse(null);
        }
        return user;
    }
}
