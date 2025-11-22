package com.hn.nutricarebe.service.impl;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.overview.DailyCountDto;
import com.hn.nutricarebe.dto.response.HeaderResponse;
import com.hn.nutricarebe.dto.response.InfoResponse;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.UserMapper;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.ProfileService;
import com.hn.nutricarebe.service.UserAllergyService;
import com.hn.nutricarebe.service.UserConditionService;
import com.hn.nutricarebe.service.UserService;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    UserRepository userRepository;
    UserMapper userMapper;
    ProfileService profileService;
    UserAllergyService userAllergyService;
    UserConditionService userConditionService;

    @Override
    public User saveOnboarding(String device) {
        User userOld = userRepository
                .findTopByDeviceIdAndStatusOrderByCreatedAtDesc(device, UserStatus.ACTIVE)
                .orElse(null);
        if (userOld != null && userOld.getProvider() == Provider.NONE) {
            userOld.setStatus(UserStatus.DELETED);
            userOld.setDeviceId(null);
            userRepository.save(userOld);
        } else if (userOld != null && userOld.getProvider() == Provider.SUPABASE_GOOGLE) {
            return userOld;
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
        return userRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    public void saveGG(User user) {
        userRepository.save(user);
    }

    @Override
    public InfoResponse getUserByToken() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        User user = userRepository.findById(userId).orElse(null);
        return InfoResponse.builder()
                .profileCreationResponse(profileService.findByUserId(userId))
                .allergies(userAllergyService.findByUser_Id(userId))
                .conditions(userConditionService.findByUser_Id(userId))
                .provider(user != null ? user.getProvider().name() : null)
                .build();
    }

    @Override
    public HeaderResponse getHeader() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());
        ProfileCreationResponse profile = profileService.findByUserId(userId);
        return HeaderResponse.builder()
                .name(profile.getName())
                .avatarUrl(profile.getAvatarUrl())
                .build();
    }

    @Override
    public long getTotalUsers() {
        return userRepository.countNonAdminUsers();
    }

    @Override
    public List<DailyCountDto> getNewUsersThisWeek() {
        String tz = "Asia/Ho_Chi_Minh";

        ZoneId zone = ZoneId.of(tz);
        ZonedDateTime now = ZonedDateTime.now(zone);

        // Thứ Hai của tuần hiện tại (theo TZ)
        LocalDate monday =
                now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toLocalDate();
        LocalDate endDate = monday.plusDays(7); // [monday, monday+7)

        // Đổi LocalDate -> Instant theo TZ
        Instant start = monday.atStartOfDay(zone).toInstant();
        Instant end = endDate.atStartOfDay(zone).toInstant();

        // Lấy tất cả createdAt trong tuần
        List<Instant> createdTimes = userRepository.findCreatedAtBetween(start, end);

        // Đếm theo LocalDate (theo TZ)
        Map<LocalDate, Long> counter = new HashMap<>();
        for (Instant ts : createdTimes) {
            LocalDate day = ts.atZone(zone).toLocalDate();
            counter.merge(day, 1L, Long::sum);
        }

        // Fill đủ 7 ngày, gán nhãn "Thứ ..."
        List<DailyCountDto> result = new ArrayList<>(7);
        for (LocalDate d = monday; d.isBefore(endDate); d = d.plusDays(1)) {
            long total = counter.getOrDefault(d, 0L);
            result.add(new DailyCountDto(toVietnameseDayLabel(d.getDayOfWeek()), d, total));
        }
        return result;
    }

    private String toVietnameseDayLabel(DayOfWeek dow) {
        if (dow == null) return "N/A";
        return switch (dow) {
            case MONDAY -> "Thứ 2";
            case TUESDAY -> "Thứ 3";
            case WEDNESDAY -> "Thứ 4";
            case THURSDAY -> "Thứ 5";
            case FRIDAY -> "Thứ 6";
            case SATURDAY -> "Thứ 7";
            case SUNDAY -> "Chủ nhật";
        };
    }

    // Đếm số người dùng mới trong 7 ngày qua
    @Override
    public long getNewUsersInLast7Days() {
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        return userRepository.countUsersCreatedAfter(sevenDaysAgo);
    }

    // Đếm số người dùng theo vai trò
    @Override
    public Map<String, Long> getUserRoleCounts() {
        long guestCount = userRepository.countByRole(Role.GUEST);
        long userCount = userRepository.countByRole(Role.USER);

        Map<String, Long> result = new HashMap<>();
        result.put("guestCount", guestCount);
        result.put("userCount", userCount);

        return result;
    }

    //  Đếm số người dùng theo trạng thái
    @Override
    public Map<String, Long> countUsersByStatus() {
        long activeCount = userRepository.countByStatus(UserStatus.ACTIVE);
        long deletedCount = userRepository.countByStatus(UserStatus.DELETED);

        Map<String, Long> result = new HashMap<>();
        result.put("active", activeCount);
        result.put("deleted", deletedCount);
        result.put("total", activeCount + deletedCount);

        return result;
    }
}
