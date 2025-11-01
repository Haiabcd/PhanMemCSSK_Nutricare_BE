package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.AllergyRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;


public interface AllergyService {
    void save(AllergyRequest request);
    void deleteById(UUID id);
    Slice<AllergyResponse> getAll(Pageable pageable);
    AllergyResponse getById(UUID id);
    Slice<AllergyResponse> searchByName(String name, Pageable pageable);
    void update(UUID id, AllergyRequest allergy);
    long getTotalAllergies();
}
