package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.*;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.MealPlanDayService;
import com.hn.nutricarebe.service.ProfileService;
import com.hn.nutricarebe.service.UserAllergyService;
import com.hn.nutricarebe.service.UserConditionService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileServiceImpl implements ProfileService {

    ProfileRepository profileRepository;
    ProfileMapper profileMapper;
    UserAllergyService userAllergyService;
    UserConditionService userConditionService;
    MealPlanDayService mealPlanDayService;


    @Override
    public ProfileCreationResponse save(ProfileCreationRequest request, User user) {
        Profile profile = profileMapper.toProfile(request);
        profile.setUser(user);
        Profile savedProfile = profileRepository.save(profile);
        return profileMapper.toProfileCreationResponse(savedProfile);
    }

    @Override
    public ProfileCreationResponse findByUserId(UUID userId) {
        Profile p =  profileRepository.findByUser_Id(userId).orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        return profileMapper.toProfileCreationResponse(p);
    }

    @Override
    public void updateAvatarAndName(String avatarUrl, String name, UUID userId) {
        Profile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        profile.setName(name);
        profile.setAvataUrl(avatarUrl);

        profileRepository.save(profile);
    }


    @Transactional
    public void updateProfile(UpdateRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        ProfileUpdateRequest profileRequest = request.getProfile();

        Profile profile = profileRepository.findById(profileRequest.getId())
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        profile.setHeightCm(profileRequest.getHeightCm());
        profile.setWeightKg(profileRequest.getWeightKg());
        profile.setGender(profileRequest.getGender());
        profile.setBirthYear(profileRequest.getBirthYear());
        profile.setGoal(profileRequest.getGoal());
        switch (profileRequest.getGoal()) {
            case LOSE -> {
                profile.setTargetWeightDeltaKg(-Math.abs(profileRequest.getTargetWeightDeltaKg()));
                profile.setTargetDurationWeeks(profileRequest.getTargetDurationWeeks());
            }
            case MAINTAIN -> {
                profile.setTargetWeightDeltaKg(0);
                profile.setTargetDurationWeeks(0);
            }
            default -> {
                profile.setTargetWeightDeltaKg(Math.abs(profileRequest.getTargetWeightDeltaKg()));
                profile.setTargetDurationWeeks(profileRequest.getTargetDurationWeeks());
            }
        }
        profile.setActivityLevel(profileRequest.getActivityLevel());
        profile.setName(profileRequest.getName());
        Profile profileSave =  profileRepository.save(profile);
        userAllergyService.updateUserAllergys(userId, request.getAllergies());
        userConditionService.updateUserConditions(userId, request.getConditions());
        mealPlanDayService.removeFromDate(request.getStartDate(), userId);

        ProfileCreationRequest profileCreationRequest = profileMapper.toProfileCreationRequest(profileSave);
        MealPlanCreationRequest mealPlanCreationRequest = MealPlanCreationRequest.builder()
                .userId(userId)
                .profile(profileCreationRequest)
                .build();
        mealPlanDayService.createPlan(mealPlanCreationRequest, 7);
    }


}
