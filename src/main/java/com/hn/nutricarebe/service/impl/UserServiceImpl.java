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
import com.hn.nutricarebe.service.AuthService;
import com.hn.nutricarebe.service.UserService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;


    @Override
    public User saveOnboarding(String device) {
        if(userRepository.existsByDeviceId(device)){
            throw new AppException(ErrorCode.DEVICE_ID_EXISTED);
        }

        User user = User.builder()
                .deviceId(device)
                .role(Role.GUEST)
                .provider(Provider.NONE)
                .status(UserStatus.ACTIVE)
                .build();
        return userRepository.save(user);
    }

    @Override
    public User getUserById(UUID id) {
        User u =  userRepository.findById(id).
                orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return u;
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

    @Override
    public User getUserByToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);

        UUID userId = UUID.fromString(auth.getName());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return user;
    }

}
