package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.response.OnboardingResponse;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.mapper.UserMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.AuthService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    ProfileRepository profileRepository;
    UserMapper userMapper;
    ProfileMapper profileMapper;

    @Override
    public OnboardingResponse onBoarding(OnboardingRequest request) {
        //Lưu user
        if(userRepository.existsByDeviceId(request.getUser().getDeviceId())){
            throw new AppException(ErrorCode.DEVICE_ID_EXISTED);
        }
        User user =  userMapper.toUser(request.getUser());
        user.setRole(Role.GUEST);
        user.setProvider(Provider.NONE);
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        UserCreationResponse userCreationResponse = userMapper.toUserCreationResponse(savedUser);
        //Lưu profile
        if(profileRepository.existsByUserId(savedUser.getId())){
            throw new AppException(ErrorCode.USERID_EXISTED);
        }
        Profile profile = profileMapper.toProfile(request.getProfile());
        profile.setUser(savedUser);
        Profile savedProfile = profileRepository.save(profile);
        ProfileCreationResponse profileCreationResponse = profileMapper.toProfileCreationResponse(savedProfile);
        //Lập kế hoạch
        //Tạo token
        //Trả về
        return OnboardingResponse.builder()
                .user(userCreationResponse)
                .profile(profileCreationResponse)
                .build();

    }
}
