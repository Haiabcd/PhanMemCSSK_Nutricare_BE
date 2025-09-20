package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.*;
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
import com.hn.nutricarebe.service.UserAllergyService;
import com.hn.nutricarebe.service.UserConditionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    ProfileRepository profileRepository;
    UserMapper userMapper;
    ProfileMapper profileMapper;
    UserAllergyService userAllergyService;
    UserConditionService userConditionService;


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
        Profile profile = profileMapper.toProfile(request.getProfile());
        profile.setUser(savedUser);
        Profile savedProfile = profileRepository.save(profile);
        ProfileCreationResponse profileCreationResponse = profileMapper.toProfileCreationResponse(savedProfile);
        //Lưu bệnh nền
        Set<UUID> conditionIds = request.getConditions();
        List<UserConditionResponse> listCondition = new ArrayList<>();
        if(conditionIds != null && !conditionIds.isEmpty()){
            listCondition = userConditionService.saveUserCondition(UserConditionCreationRequest.builder()
                    .user(savedUser)
                    .conditionIds(conditionIds)
                    .build());
        }
        //Lưu dị ứng
        Set<UUID> allergyIds = request.getAllergies();
        List<UserAllergyResponse> listAllergy = new ArrayList<>();
        if(allergyIds != null && !allergyIds.isEmpty()){
             UserAllergyCreationRequest uar = UserAllergyCreationRequest.builder()
                    .user(savedUser)
                    .allergyIds(allergyIds)
                    .build();
            listAllergy = userAllergyService.saveUserAllergy(uar);
        }
        //Lập kế hoạch
        //Tạo token
        //Trả về
        return OnboardingResponse.builder()
                .user(userCreationResponse)
                .profile(profileCreationResponse)
                .conditions(listCondition)
                .allergies(listAllergy)
                .build();
    }
}
