package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.*;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.service.PlanOrchestrator;
import com.hn.nutricarebe.service.ProfileService;
import com.hn.nutricarebe.service.UserAllergyService;
import com.hn.nutricarebe.service.UserConditionService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileServiceImpl implements ProfileService {
    ProfileRepository profileRepository;
    ProfileMapper profileMapper;
    UserAllergyService userAllergyService;
    UserConditionService userConditionService;
    PlanOrchestrator planOrchestrator;


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
    public ProfileCreationRequest findByUserId_request(UUID userId) {
        Profile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        return profileMapper.toProfileCreationRequest(profile);
    }

    @Override
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

        // 1) Cập nhật allergies/conditions và biết chúng có thay đổi không
        boolean allergyUpdated = userAllergyService.updateUserAllergys(userId, request.getAllergies());
        boolean conditionUpdated = userConditionService.updateUserConditions(userId, request.getConditions());

        // 2) Tính toán giá trị "kỳ vọng" cho các field ảnh hưởng meal plan từ request
        Integer expectedTargetDeltaKg;
        Integer expectedTargetDurationWeeks;

        switch (profileRequest.getGoal()) {
            case LOSE -> {
                expectedTargetDeltaKg = -Math.abs(profileRequest.getTargetWeightDeltaKg());
                expectedTargetDurationWeeks = profileRequest.getTargetDurationWeeks();
            }
            case MAINTAIN -> {
                expectedTargetDeltaKg = 0;
                expectedTargetDurationWeeks = 0;
            }
            default -> {
                expectedTargetDeltaKg = Math.abs(profileRequest.getTargetWeightDeltaKg());
                expectedTargetDurationWeeks = profileRequest.getTargetDurationWeeks();
            }
        }

        // 3) Kiểm tra các thay đổi "có ý nghĩa" đối với meal plan (bỏ qua name)
        boolean profileAffectsPlanChanged =
                !Objects.equals(profile.getHeightCm(),       profileRequest.getHeightCm()) ||
                        !Objects.equals(profile.getWeightKg(),       profileRequest.getWeightKg()) ||
                        !Objects.equals(profile.getGender(),         profileRequest.getGender())   ||
                        !Objects.equals(profile.getBirthYear(),      profileRequest.getBirthYear())||
                        !Objects.equals(profile.getGoal(),           profileRequest.getGoal())     ||
                        !Objects.equals(profile.getActivityLevel(),  profileRequest.getActivityLevel()) ||
                        !Objects.equals(profile.getTargetWeightDeltaKg(), expectedTargetDeltaKg) ||
                        !Objects.equals(profile.getTargetDurationWeeks(), expectedTargetDurationWeeks);

        // 4) Gán cập nhật vào profile (bao gồm name)
        profile.setHeightCm(profileRequest.getHeightCm());
        profile.setWeightKg(profileRequest.getWeightKg());
        profile.setGender(profileRequest.getGender());
        profile.setBirthYear(profileRequest.getBirthYear());
        profile.setGoal(profileRequest.getGoal());
        profile.setActivityLevel(profileRequest.getActivityLevel());
        profile.setName(profileRequest.getName());
        profile.setTargetWeightDeltaKg(expectedTargetDeltaKg);
        profile.setTargetDurationWeeks(expectedTargetDurationWeeks);

        profileRepository.save(profile);

        boolean shouldRecreateMealPlan = allergyUpdated || conditionUpdated || profileAffectsPlanChanged;

        if (shouldRecreateMealPlan) {
            planOrchestrator.updatePlan(request.getStartDate(), userId);
        }
    }
}
