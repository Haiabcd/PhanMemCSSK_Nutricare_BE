package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.AllergyCreationRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.AllergyMapper;
import com.hn.nutricarebe.repository.AllergyRepository;
import com.hn.nutricarebe.service.AllergyService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AllergyServiceImpl implements AllergyService {
    AllergyRepository allergyRepository;
    AllergyMapper allergyMapper;

    @Override
    public AllergyResponse save(AllergyCreationRequest request) {

        if (allergyRepository.existsByName(request.getName())) {
            throw new AppException(ErrorCode.ALLERGY_EXISTED);
        }

        Allergy savedAllergy = allergyRepository.save(allergyMapper.toAllergy(request));
        return allergyMapper.toAllergyResponse(savedAllergy);
    }

    @Override
    public void deleteById(UUID id) {

    }

    // Lấy danh sách tất cả dị ứng
    @Override
    public Slice<AllergyResponse> getAll(Pageable pageable) {
        Slice<Allergy> allergy = allergyRepository.findAllBy(pageable);
        return allergy.map(allergyMapper::toAllergyResponse);
    }

    @Override
    public AllergyResponse getById(UUID id) {
        return null;
    }

    @Override
    public Slice<AllergyResponse> searchByName(String name, Pageable pageable) {
        return null;
    }


}


