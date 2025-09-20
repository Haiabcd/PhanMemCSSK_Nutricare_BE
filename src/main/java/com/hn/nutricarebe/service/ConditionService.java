package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.entity.Condition;

import java.util.List;

public interface ConditionService {
    public List<Condition> findAll();
    public ConditionResponse save(ConditionCreationRequest request);
    public Boolean deleteById(String id);
    public Condition findById(String id);
}
