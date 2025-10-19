package com.hn.nutricarebe.orchestrator.impl;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.orchestrator.ProfileOrchestrator;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.service.ProfileService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileOrchestratorImpl implements ProfileOrchestrator {
    ProfileRepository profileRepository;
    ProfileMapper profileMapper;

    @Override
    public ProfileCreationRequest getByUserId_request(UUID userId){
        Profile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        return profileMapper.toProfileCreationRequest(profile);
    }
}
