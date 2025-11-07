package com.hn.nutricarebe.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.request.AllergyRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.entity.Allergy;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.AllergyMapper;
import com.hn.nutricarebe.mapper.NutritionRuleMapper;
import com.hn.nutricarebe.repository.AllergyRepository;
import com.hn.nutricarebe.repository.NutritionRuleRepository;
import com.hn.nutricarebe.repository.UserAllergyRepository;
import com.hn.nutricarebe.service.AllergyService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AllergyServiceImpl implements AllergyService {
    AllergyRepository allergyRepository;
    NutritionRuleRepository nutritionRuleRepository;
    AllergyMapper allergyMapper;
    NutritionRuleMapper nutritionRuleMapper;
    UserAllergyRepository userAllergyRepository;

    // Tạo mới một dị ứng
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void save(AllergyRequest request) {
        if (allergyRepository.existsByNameIgnoreCase(request.getName().strip())) {
            throw new AppException(ErrorCode.ALLERGY_EXISTED);
        }
        allergyRepository.save(allergyMapper.toAllergy(request));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteById(UUID id) {
        Allergy allergy =
                allergyRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.ALLERGY_NOT_FOUND));
        // 1) Nếu có user đang dùng -> chặn xóa
        if (userAllergyRepository.existsByAllergy_Id(id)) {
            throw new AppException(ErrorCode.DELETE_ALLERGY_CONFLICT);
        }
        // 2) Nếu chưa ai dùng -> dọn các NutritionRule liên quan rồi xóa Allergy
        if (nutritionRuleRepository.existsByAllergy_Id(id)) {
            nutritionRuleRepository.deleteByAllergyId(id);
        }
        allergyRepository.delete(allergy);
    }

    // Lấy danh sách tất cả dị ứng
    @Override
    public Slice<AllergyResponse> getAll(Pageable pageable) {
        Slice<Allergy> slice = allergyRepository.findAllBy(pageable);
        List<Allergy> content = slice.getContent();

        if (content.isEmpty()) {
            return slice.map(allergyMapper::toAllergyResponse);
        }
        Set<UUID> ids = content.stream().map(Allergy::getId).collect(java.util.stream.Collectors.toSet());
        List<NutritionRule> rules = nutritionRuleRepository.findByActiveTrueAndAllergy_IdIn(ids);
        Map<UUID, List<NutritionRule>> rulesByAllergy = rules.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        nr -> nr.getAllergy().getId()));
        return slice.map(a -> allergyMapper.toAllergyResponse(a, rulesByAllergy, nutritionRuleMapper));
    }

    // Tìm một dị ứng theo id
    @Override
    public AllergyResponse getById(UUID id) {
        Allergy allergy =
                allergyRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.ALLERGY_NOT_FOUND));
        Set<UUID> ids = Set.of(allergy.getId());
        List<NutritionRule> rules = nutritionRuleRepository.findByActiveTrueAndAllergy_IdIn(ids);
        Map<UUID, List<NutritionRule>> rulesByAllergy = rules.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        nr -> nr.getAllergy().getId()));
        return allergyMapper.toAllergyResponse(allergy, rulesByAllergy, nutritionRuleMapper);
    }

    // Tìm kiếm dị ứng theo tên
    @Override
    public Slice<AllergyResponse> searchByName(String name, Pageable pageable) {
        String q = name == null ? "" : name.trim();
        Slice<Allergy> slice = allergyRepository.findByNameContainingIgnoreCase(q, pageable);
        return slice.map(allergyMapper::toAllergyResponse);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void update(UUID id, AllergyRequest allergy) {
        Allergy existing =
                allergyRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.ALLERGY_NOT_FOUND));
        if (allergy.getName() != null) {
            String newName = allergy.getName().trim();
            if (!newName.equalsIgnoreCase(existing.getName().strip())) {
                if (allergyRepository.existsByNameIgnoreCase(newName)) {
                    throw new AppException(ErrorCode.ALLERGY_EXISTED);
                }
                existing.setName(newName);
                allergyRepository.save(existing);
            }
        }
    }

    @Override
    public long getTotalAllergies() {
        return allergyRepository.count();
    }
}
