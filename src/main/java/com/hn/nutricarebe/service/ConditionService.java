package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ConditionRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import java.util.UUID;

public interface ConditionService {
    void save(ConditionRequest request);
    void deleteById(UUID id);
    Slice<ConditionResponse> getAll(Pageable pageable);
    ConditionResponse getById(UUID id);
    Slice<ConditionResponse> searchByName(String name, Pageable pageable);
    long getTotalConditions();
    void update(UUID id, ConditionRequest condition);
}
