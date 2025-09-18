package com.hn.nutricarebe.service;


import com.hn.nutricarebe.dto.request.AllergyCreationRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.entity.Allergy;

import java.util.List;


public interface AllergyService {
    public List<Allergy> findAll();
    public AllergyResponse save(AllergyCreationRequest request);

}
