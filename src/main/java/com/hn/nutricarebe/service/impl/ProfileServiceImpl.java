package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.ProfileService;
import com.hn.nutricarebe.service.UserService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileServiceImpl implements ProfileService {

    ProfileRepository profileRepository;
    ProfileMapper profileMapper;


    @Override
    public ProfileCreationResponse save(ProfileCreationRequest request, User user) {
        Profile profile = profileMapper.toProfile(request);
        profile.setUser(user);
        Profile savedProfile = profileRepository.save(profile);
        return profileMapper.toProfileCreationResponse(savedProfile);
    }

    @Override
    public void updateAvatarAndName(String avatarUrl, String name, UUID userId) {
        Profile profile = profileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
        profile.setName(name);
        profile.setAvataUrl(avatarUrl);

        profileRepository.save(profile);
    }
}
