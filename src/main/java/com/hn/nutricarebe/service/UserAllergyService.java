package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.response.UserAllergyResponse;

import java.util.List;

public interface UserAllergyService {
   public List<UserAllergyResponse> saveUserAllergy(UserAllergyCreationRequest request);
}
