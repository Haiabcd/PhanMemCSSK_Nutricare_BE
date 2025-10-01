package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;


public interface ConditionService {
    public ConditionResponse save(ConditionCreationRequest request);

}
