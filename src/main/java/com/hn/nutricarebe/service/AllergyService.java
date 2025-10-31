package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.AllergyCreationRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.entity.Allergy;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.UUID;


public interface AllergyService {
    public AllergyResponse save(AllergyCreationRequest request);
    public void deleteById(UUID id);
    public Slice<AllergyResponse> getAll(Pageable pageable);
    public AllergyResponse getById(UUID id);
    public Slice<AllergyResponse> searchByName(String name, Pageable pageable);
    public AllergyResponse update(UUID id, Allergy allergy);
    public long getTotalAllergies();
}
