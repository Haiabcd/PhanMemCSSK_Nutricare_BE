package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.AllergyCreationRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.AllergyMapper;
import com.hn.nutricarebe.repository.AllergyRepository;
import com.hn.nutricarebe.service.AllergyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AllergyServiceImpl implements AllergyService {
    AllergyRepository allergyRepository;
    AllergyMapper allergyMapper;


    @Override
    public List<Allergy> findAll() {
        return allergyRepository.findAll();
    }

    @Override
    public AllergyResponse save(AllergyCreationRequest request) {
        if (allergyRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.ALLERGY_EXISTED);
        }

        Allergy savedAllergy = allergyRepository.save(allergyMapper.toAllergy(request));
        return allergyMapper.toAllergyResponse(savedAllergy);
    }
}


