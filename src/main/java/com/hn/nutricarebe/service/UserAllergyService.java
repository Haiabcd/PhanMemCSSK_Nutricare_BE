package com.hn.nutricarebe.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;

public interface UserAllergyService {
    void saveUserAllergy(UserAllergyCreationRequest request);

    List<UserAllergyResponse> findByUser_Id(UUID userId);

    boolean updateUserAllergys(UUID userId, Set<UUID> allergyIds);

    List<Map<String, Object>> getTop5AllergyNames();
}
