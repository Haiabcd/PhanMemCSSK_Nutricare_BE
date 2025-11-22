package com.hn.nutricarebe.service.impl;

import static com.hn.nutricarebe.helper.ProfileHelper.*;

import java.time.LocalDate;
import java.util.*;
import com.hn.nutricarebe.entity.WeightLog;
import com.hn.nutricarebe.repository.WeightLogRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hn.nutricarebe.dto.ai.ProfileAI;
import com.hn.nutricarebe.dto.request.*;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;
import com.hn.nutricarebe.dto.response.UserConditionResponse;
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

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileServiceImpl implements ProfileService {
    ProfileRepository profileRepository;
    WeightLogRepository weightLogRepository;
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
        Profile p = profileRepository
                .findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        return profileMapper.toProfileCreationResponse(p);
    }

    @Override
    public ProfileCreationRequest findByUserId_request(UUID userId) {
        Profile profile = profileRepository
                .findByUser_Id(userId)
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
        Profile oldProfile = profileRepository
                .findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        // 1) Cập nhật allergies/conditions
        boolean allergyUpdated = userAllergyService.updateUserAllergys(userId, request.getAllergies());
        boolean conditionUpdated = userConditionService.updateUserConditions(userId, request.getConditions());

        // 2) Tính giá trị "kỳ vọng" cho target
        int newTargetKg;
        Integer newTargetWeeks;

        switch (profileRequest.getGoal()) {
            case LOSE -> {
                newTargetKg = -Math.abs(profileRequest.getTargetWeightDeltaKg());
                newTargetWeeks = profileRequest.getTargetDurationWeeks();
            }
            case MAINTAIN -> {
                newTargetKg = 0;
                newTargetWeeks = 0;
            }
            default -> {
                newTargetKg = Math.abs(profileRequest.getTargetWeightDeltaKg());
                newTargetWeeks = profileRequest.getTargetDurationWeeks();
            }
        }

        // 3) So sánh giá trị cũ vs request (trước khi set)
        Integer oldHeight = oldProfile.getHeightCm();
        Integer oldWeight = oldProfile.getWeightKg();
        var oldGender = oldProfile.getGender();
        Integer oldBirthYear = oldProfile.getBirthYear();
        var oldGoal = oldProfile.getGoal();
        var oldActivity = oldProfile.getActivityLevel();
        Integer oldDelta = oldProfile.getTargetWeightDeltaKg();
        Integer oldDuration = oldProfile.getTargetDurationWeeks();

        boolean profileAffectsPlanChanged =
                !Objects.equals(oldHeight, profileRequest.getHeightCm())
                        || !Objects.equals(oldWeight, profileRequest.getWeightKg())
                        || !Objects.equals(oldGender, profileRequest.getGender())
                        || !Objects.equals(oldBirthYear, profileRequest.getBirthYear())
                        || !Objects.equals(oldGoal, profileRequest.getGoal())
                        || !Objects.equals(oldActivity, profileRequest.getActivityLevel())
                        || !Objects.equals(oldDelta, newTargetKg)
                        || !Objects.equals(oldDuration, newTargetWeeks);

        boolean checkArchive =
                !Objects.equals(oldWeight, profileRequest.getWeightKg())
                        || !Objects.equals(oldGoal, profileRequest.getGoal())
                        || !Objects.equals(oldDelta, newTargetKg)
                        || !Objects.equals(oldDuration, newTargetWeeks);

        // 4) Gán cập nhật vào profile
        oldProfile.setHeightCm(profileRequest.getHeightCm());
        oldProfile.setWeightKg(profileRequest.getWeightKg());
        oldProfile.setGender(profileRequest.getGender());
        oldProfile.setBirthYear(profileRequest.getBirthYear());
        oldProfile.setGoal(profileRequest.getGoal());
        oldProfile.setActivityLevel(profileRequest.getActivityLevel());
        oldProfile.setName(profileRequest.getName());
        oldProfile.setTargetWeightDeltaKg(newTargetKg);
        oldProfile.setTargetDurationWeeks(newTargetWeeks);

        // 5) Cập nhật bảng theo dõi cân nặng
        if (profileRequest.getGoal() != GoalType.MAINTAIN
                && !Objects.equals(oldWeight, profileRequest.getWeightKg())) {
            LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
            upsertWeightLog(oldProfile, today, profileRequest.getWeightKg());
        }
        // 6) Kiểm tra và cập nhật trạng thái đạt mục tiêu
        GoalType requestGoal = profileRequest.getGoal();  //Mục tiêu từ request
        int currentWeight = profileRequest.getWeightKg();  //Cân nặng hiện tại từ request
        int delta = Math.abs(oldProfile.getTargetWeightDeltaKg());  //Giá trị tuyệt đối của delta mục tiêu
        if (checkArchive) {
            if (!Objects.equals(oldGoal, requestGoal)
                    || !Objects.equals(oldDelta, newTargetKg)
                    || !Objects.equals(oldDuration, newTargetWeeks)) {
                oldProfile.setSnapWeightKg(currentWeight);
            }
            Integer snap = oldProfile.getSnapWeightKg();
            if (snap != null) {
                if (requestGoal == GoalType.LOSE) {
                    oldProfile.setGoalReached(currentWeight <= snap - delta);
                } else if (requestGoal == GoalType.GAIN) {
                    oldProfile.setGoalReached(currentWeight >= snap + delta);
                } else {
                    oldProfile.setGoalReached(true);
                }
            }
        }

        // 7) Lưu profile và cập nhật meal plan nếu cần
        profileRepository.save(oldProfile);
        boolean shouldRecreateMealPlan = allergyUpdated || conditionUpdated || profileAffectsPlanChanged;
        if (shouldRecreateMealPlan) {
            mealPlanDayService.updatePlanForOneDay(request.getStartDate(), userId);
            LocalDate tomorrow = request.getStartDate().plusDays(1);
            mealPlanDayService.removeFromDate(tomorrow, userId);
        }
    }


    @Override
    @Transactional
    public void updateWeight(WeightUpdateRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        Profile profile = profileRepository
                .findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        Integer oldDisplayWeight = profile.getWeightKg();

        var dateNow = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        profile.setWeightKg(request.getWeightKg());

        // Ghi/cập nhật WeightLog nếu có goal giảm/tăng và cân nặng thay đổi
        if (profile.getGoal() != GoalType.MAINTAIN
                && !Objects.equals(oldDisplayWeight, request.getWeightKg())) {
            upsertWeightLog(profile, dateNow, request.getWeightKg());
        }

        int delta = Math.abs(profile.getTargetWeightDeltaKg());
        int currentWeight = request.getWeightKg();
        Integer snap = profile.getSnapWeightKg();
        if (snap == null) {
            profile.setGoalReached(false);
        } else {
            if (profile.getGoal() == GoalType.LOSE) {
                profile.setGoalReached(currentWeight <= snap - delta);
            } else if (profile.getGoal() == GoalType.GAIN) {
                profile.setGoalReached(currentWeight >= snap + delta);
            } else {
                profile.setGoalReached(true);
            }
        }

        profileRepository.save(profile);

        boolean weightChanged = !Objects.equals(oldDisplayWeight, request.getWeightKg());
        if (weightChanged) {
            mealPlanDayService.updatePlanForOneDay(dateNow, userId);
            LocalDate tomorrow = dateNow.plusDays(1);
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

        List<String> conditions = userConditionService.findByUser_Id(userId).stream()
                .map(UserConditionResponse::getName)
                .toList();

        List<String> allergies = userAllergyService.findByUser_Id(userId).stream()
                .map(UserAllergyResponse::getName)
                .toList();

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

    // Thống kê mục tiêu của người dùng
    @Override
    public Map<String, Long> getGoalStats() {
        long gain = profileRepository.countByGoal(GoalType.GAIN);
        long lose = profileRepository.countByGoal(GoalType.LOSE);
        long maintain = profileRepository.countByGoal(GoalType.MAINTAIN);

        Map<String, Long> stats = new HashMap<>();
        stats.put("gain", gain);
        stats.put("lose", lose);
        stats.put("maintain", maintain);

        return stats;
    }


    //Thống kê số người dùng đạt mục tiêu
    @Override
    public long getCompletedGoalsCount() {
        return profileRepository.countCompletedGoals();
    }

    //==================================HELPER METHODS==================================//
    private void upsertWeightLog(Profile profile, LocalDate date, int weightKg) {
        weightLogRepository
                .findByProfile_IdAndLoggedAt(profile.getId(), date)
                .ifPresentOrElse(
                        existing -> existing.setWeightKg(weightKg),
                        () -> weightLogRepository.save(
                                WeightLog.builder()
                                        .profile(profile)
                                        .loggedAt(date)
                                        .weightKg(weightKg)
                                        .build()
                        )
                );
    }

}
