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
    public boolean updateUserAllergys(UUID userId, Set<UUID> allergyIds) {
        Set<UUID> newIds = (allergyIds == null) ? Collections.emptySet()
                : new LinkedHashSet<>(allergyIds);

        List<UserAllergy> current = userAllergyRepository.findByUser_Id(userId);
        Set<UUID> currentIds = current.stream()
                .map(ua -> ua.getAllergy().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (Objects.equals(currentIds, newIds)) {
            return false;
        }

        if (newIds.isEmpty()) {
            userAllergyRepository.deleteAllByUserId(userId);
            return true;
        }
        Set<UUID> toRemove = new LinkedHashSet<>(currentIds);
        toRemove.removeAll(newIds);

        Set<UUID> toAdd = new LinkedHashSet<>(newIds);
        toAdd.removeAll(currentIds);

        if (!toRemove.isEmpty()) {
            userAllergyRepository.deleteByUserIdAndAllergyIdIn(userId, toRemove);
        }

        if (!toAdd.isEmpty()) {
            List<Allergy> addEntities = allergyRepository.findAllById(toAdd);
            List<UserAllergy> newLinks = addEntities.stream()
                    .map(a -> UserAllergy.builder()
                            .user(User.builder().id(userId).build())
                            .allergy(a)
                            .build())
                    .collect(Collectors.toList());
            userAllergyRepository.saveAll(newLinks);
        }
        return true;
    }

    public List<Map<String, Object>> getTop5AllergyNames() {
        List<Object[]> rows = userAllergyRepository.findTopAllergyNames();

        List<Map<String, Object>> result = new ArrayList<>();

        for (int i = 0; i < Math.min(rows.size(), 5); i++) {
            Object[] row = rows.get(i);
            String name = (String) row[0];
            Long total = (Long) row[1];

            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            map.put("total", total);

            result.add(map);
        }

        return result;
    }

}
