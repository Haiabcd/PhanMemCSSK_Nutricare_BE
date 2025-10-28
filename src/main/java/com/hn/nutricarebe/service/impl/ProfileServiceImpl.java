package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.ai.ProfileAI;
import com.hn.nutricarebe.dto.request.*;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;
import com.hn.nutricarebe.dto.response.UserConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.GoalType;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.service.*;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.hn.nutricarebe.helper.ProfileHelper.*;


@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileServiceImpl implements ProfileService {
    ProfileRepository profileRepository;
    ProfileMapper profileMapper;
    UserAllergyService userAllergyService;
    UserConditionService userConditionService;
    MealPlanDayService mealPlanDayService;
    NutritionRuleService nutritionRuleService;


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
            mealPlanDayService.updatePlanForOneDay(request.getStartDate(), userId);
            LocalDate tomorrow = request.getStartDate().plusDays(1);
            mealPlanDayService.removeFromDate(tomorrow, userId);
        }
    }


    @Override
    public ProfileAI getHealthProfile(UUID userId) {
        Profile p = profileRepository.findByUser_Id(userId).orElse(null);
        if (p == null) {
            return null;
        }

        List<NutritionRule> rules = nutritionRuleService.getRuleByUserId(userId);
        List<String> messages = rules.stream()
                .map(NutritionRule::getMessage)
                .filter(Objects::nonNull)
                .toList();

        List<String> conditions = userConditionService.findByUser_Id(userId)
                .stream().map(UserConditionResponse::getName).toList();

        List<String> allergies = userAllergyService.findByUser_Id(userId)
                .stream().map(UserAllergyResponse::getName).toList();

        // Tính tuổi từ birthYear
        int currentYear = LocalDate.now().getYear();
        Integer age = (p.getBirthYear() != null) ? currentYear - p.getBirthYear() : null;

        return ProfileAI.builder()
                .conditions(conditions)
                .allergies(allergies)
                .age(age)
                .heightCm(p.getHeightCm() != null ? p.getHeightCm() : null)
                .weightKg(p.getWeightKg() != null ? p.getWeightKg() : null)
                .goal(buildGoalText(p))
                .activityLevel(buildActivityLevel(p.getActivityLevel()))
                .gender(buildGenderText(p.getGender()))
                .nutritionRules(messages)
                .build();
    }



}
