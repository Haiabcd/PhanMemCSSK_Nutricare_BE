package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.UUID;

public interface ConditionService {
    public ConditionResponse save(ConditionCreationRequest request);
    public void deleteById(UUID id);
    public Slice<ConditionResponse> getAll(Pageable pageable);
    public ConditionResponse getById(UUID id);
    public Slice<ConditionResponse> searchByName(String name, Pageable pageable);
}
