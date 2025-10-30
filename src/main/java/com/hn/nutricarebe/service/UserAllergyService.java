package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface UserAllergyService {
  List<UserAllergyResponse> saveUserAllergy(UserAllergyCreationRequest request);
  List<UserAllergyResponse> findByUser_Id(UUID userId);
  boolean updateUserAllergys(UUID userId, Set<UUID> allergyIds);
  List<Map<String, Object>> getTop5AllergyNames();
}
