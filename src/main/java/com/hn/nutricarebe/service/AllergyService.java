package com.hn.nutricarebe.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import com.hn.nutricarebe.dto.request.AllergyRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;

public interface AllergyService {
    void save(AllergyRequest request);

    void deleteById(UUID id);

    Slice<AllergyResponse> getAll(Pageable pageable);

    AllergyResponse getById(UUID id);

    Slice<AllergyResponse> searchByName(String name, Pageable pageable);

    void update(UUID id, AllergyRequest allergy);

    long getTotalAllergies();
}
