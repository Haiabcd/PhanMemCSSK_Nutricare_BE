package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.entity.UserAllergy;
import com.hn.nutricarebe.mapper.AllergyMapper;
import com.hn.nutricarebe.repository.AllergyRepository;
import com.hn.nutricarebe.repository.UserAllergyRepository;
import com.hn.nutricarebe.service.UserAllergyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAllergyServiceImpl implements UserAllergyService {

    UserAllergyRepository userAllergyRepository;
    AllergyRepository allergyRepository;
    AllergyMapper allergyMapper;

    @Override
    public List<UserAllergyResponse> findByUser_Id(UUID userId) {
        return userAllergyRepository.findByUser_Id(userId).stream()
                .map(ua -> allergyMapper.toUserAllergyResponse(ua.getAllergy()))
                .toList();
    }

    @Override
    public List<UserAllergyResponse> saveUserAllergy(UserAllergyCreationRequest request) {
        User u = request.getUser();
        List<UserAllergyResponse> response = new ArrayList<>();
        for(UUID allergyId : request.getAllergyIds()) {
            Allergy a = allergyRepository.findById(allergyId).orElse(null);
            if(a != null) {
                UserAllergy ua = UserAllergy.builder()
                        .user(u)
                        .allergy(a)
                        .build();
                userAllergyRepository.save(ua);
                response.add(allergyMapper.toUserAllergyResponse(a));
            }
        }
        return response;
    }
}
