package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.config.AppTimeConfig;
import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;
import com.hn.nutricarebe.repository.WaterLogRepository;
import com.hn.nutricarebe.service.WaterLogService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.entity.WaterLog;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WaterLogServiceImpl implements WaterLogService {
    WaterLogRepository waterLogRepository;
    AppTimeConfig appTimeConfig;
    ZoneId appZone = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    public void create(WaterLogCreationRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        Instant drankAt = request.getDrankAt();
        LocalDate businessDate = LocalDate.ofInstant(drankAt, appZone);
        User userRef = User.builder().id(userId).build();
        WaterLog entity = WaterLog.builder()
                .user(userRef)
                .date(businessDate)
                .drankAt(drankAt)
                .amountMl(request.getAmountMl())
                .build();
       waterLogRepository.save(entity);
    }
}
