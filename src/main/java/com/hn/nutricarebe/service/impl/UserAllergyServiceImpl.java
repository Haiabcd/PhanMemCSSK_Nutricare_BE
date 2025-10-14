package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;
import com.hn.nutricarebe.entity.*;
import com.hn.nutricarebe.mapper.AllergyMapper;
import com.hn.nutricarebe.repository.AllergyRepository;
import com.hn.nutricarebe.repository.UserAllergyRepository;
import com.hn.nutricarebe.service.UserAllergyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

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

    @Override
    public void updateUserAllergys(UUID userId, Set<UUID> allergyIds) {
        if (allergyIds == null || allergyIds.isEmpty()) {
            userAllergyRepository.deleteAllByUserId(userId);
            return;
        }

        List<UserAllergy> current = userAllergyRepository.findByUser_Id(userId);
        Set<UUID> currentIds = current.stream()
                .map(uc -> uc.getAllergy().getId())
                .collect(Collectors.toSet());

        Set<UUID> toAdd = new LinkedHashSet<>(allergyIds);
        toAdd.removeAll(currentIds);

        Set<UUID> toRemove = new LinkedHashSet<>(currentIds);
        toRemove.removeAll(allergyIds);

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            return;
        }

        if (!toRemove.isEmpty()) {
            userAllergyRepository.deleteByUserIdAndAllergyIdIn(userId, toRemove);
        }

        if (!toAdd.isEmpty()) {
            List<Allergy> conditionsToAdd = allergyRepository.findAllById(toAdd);
            List<UserAllergy> newLinks = conditionsToAdd.stream()
                    .map(c -> UserAllergy.builder()
                            .user(User.builder().id(userId).build())
                            .allergy(c)
                            .build())
                    .collect(Collectors.toList());
            userAllergyRepository.saveAll(newLinks);
        }
    }
}
