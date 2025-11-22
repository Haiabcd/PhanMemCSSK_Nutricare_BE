package com.hn.nutricarebe.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.UserConditionResponse;

public interface UserConditionService {
    void saveUserCondition(UserConditionCreationRequest request);

    List<UserConditionResponse> findByUser_Id(UUID userId);

    boolean updateUserConditions(UUID userId, Set<UUID> conditionIds);

    List<Map<String, Object>> getTop5ConditionNames();
}
