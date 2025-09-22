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
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    ProfileRepository profileRepository;
    UserMapper userMapper;
    ProfileMapper profileMapper;
    UserAllergyService userAllergyService;
    UserConditionService userConditionService;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @Override
    @Transactional
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
                .token(generateToken(savedUser))
                .profile(profileCreationResponse)
                .conditions(listCondition)
                .allergies(listAllergy)
                .build();
    }

    private String generateToken(User user){
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .issuer("nutricare.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()))
                .claim("scope", user.getRole().toString())
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Error while signing the token: {}", e.getMessage());
            throw new RuntimeException("Error while signing the token: " + e.getMessage());
        }
    }
}
