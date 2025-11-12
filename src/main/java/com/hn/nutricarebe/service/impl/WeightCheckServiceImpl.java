package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.enums.GoalType;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.WeightLogRepository;
import com.hn.nutricarebe.service.WeightCheckService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WeightCheckServiceImpl implements WeightCheckService {
    ProfileRepository profileRepo;
    WeightLogRepository logRepo;

    @Override
    public boolean mustUpdateWeight() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());


        Profile profile = profileRepo.findByUser_Id(userId).orElse(null);
        if (profile == null
                ||profile.getGoal() == GoalType.MAINTAIN
                || profile.getGoal() == null
                || profile.getTargetDurationWeeks() == 0
                || profile.getTargetWeightDeltaKg() == 0
                || profile.isGoalReached()) {
            return false;
        }
        var today = LocalDate.now();
        //Tìm bản ghi cân nặng gần nhất của profile
        var lastLogOpt = logRepo.findTopByProfileOrderByLoggedAtDesc(profile);
        if (lastLogOpt.isEmpty()) return true;
        var lastLogDate = lastLogOpt.get().getLoggedAt();
        long days = ChronoUnit.DAYS.between(lastLogDate, today);
        return days >= 7;
    }
}
