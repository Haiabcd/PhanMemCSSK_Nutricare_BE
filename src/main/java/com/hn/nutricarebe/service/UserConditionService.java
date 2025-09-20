package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.UserConditionResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserConditionService {
   public List<UserConditionResponse> saveUserCondition(UserConditionCreationRequest request);
}
