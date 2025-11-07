package com.hn.nutricarebe.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.hn.nutricarebe.dto.request.ConditionRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;

public interface ConditionService {
    void save(ConditionRequest request);

    void deleteById(UUID id);

    Slice<ConditionResponse> getAll(Pageable pageable);

    ConditionResponse getById(UUID id);

    Slice<ConditionResponse> searchByName(String name, Pageable pageable);

    long getTotalConditions();

    void update(UUID id, ConditionRequest condition);
}
