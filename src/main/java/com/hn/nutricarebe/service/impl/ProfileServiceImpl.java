package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.mapper.UserMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.ProfileService;
import com.hn.nutricarebe.service.UserService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.stereotype.Service;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class ProfileServiceImpl implements ProfileService {

    ProfileRepository profileRepository;
    UserRepository userRepository;
    ProfileMapper profileMapper;

    UserService userService;

    @Override
    @Transactional
    public ProfileCreationResponse save(ProfileCreationRequest request) {
        Profile p = profileMapper.toProfile(request);
        // 1) Tìm user theo userId trong request
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User không tồn tại với id: " + request.getUserId()));
        // 2) Kiểm tra user đã có profile chưa
        if(profileRepository.existsByUserId(user.getId())){
            throw new RuntimeException("User đã có profile với id: " + user.getProfile());
        }
        // 3) Gán profile cho user
        p.setUser(user);
        // 4) Lưu profile vào database
        Profile f = profileRepository.save(p);
        return profileMapper.toProfileCreationResponse(f);
    }
}
