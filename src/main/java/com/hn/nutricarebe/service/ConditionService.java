package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.entity.Condition;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.util.List;

public interface ConditionService {
    public List<Condition> findAll();
    public Condition save(ConditionCreationRequest request);
    public Boolean deleteById(String id);
    public Condition findById(String id);
}
