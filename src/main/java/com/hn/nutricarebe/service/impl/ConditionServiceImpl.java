package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ConditionRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ConditionMapper;
import com.hn.nutricarebe.mapper.NutritionRuleMapper;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.repository.NutritionRuleRepository;
import com.hn.nutricarebe.repository.UserConditionRepository;
import com.hn.nutricarebe.service.ConditionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConditionServiceImpl implements ConditionService {

    ConditionRepository conditionRepository;
    NutritionRuleRepository nutritionRuleRepository;
    ConditionMapper conditionMapper;
    NutritionRuleMapper nutritionRuleMapper;
    UserConditionRepository userConditionRepository;

    // Tạo mới một bệnh nền
    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void save(ConditionRequest request) {
       if(conditionRepository.existsByNameIgnoreCase(request.getName().strip())) {
           throw new AppException(ErrorCode.CONDITION_EXISTED);
       }
       conditionRepository.save(conditionMapper.toCondition(request));
    }

    // Xóa một bệnh nền theo id
    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteById(UUID id) {
        Condition con = conditionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONDITION_NOT_FOUND));

        // 1) Có user đang gán bệnh nền -> chặn xoá
        if (userConditionRepository.existsByCondition_Id(id)) {
            throw new AppException(ErrorCode.DELETE_CONDITION_CONFLICT);
        }
        // 2) Không ai dùng -> dọn các rule tham chiếu tới condition rồi xoá
        if (nutritionRuleRepository.existsByCondition_Id(id)) {
            nutritionRuleRepository.deleteByConditionId(id);
        }
        conditionRepository.delete(con);
    }

    // Lấy danh sách tất cả bệnh nền
    @Override
    public Slice<ConditionResponse> getAll(Pageable pageable) {
        Slice<Condition> slice = conditionRepository.findAllBy(pageable);
        List<Condition> content = slice.getContent();
        if (content.isEmpty()) {
            return slice.map(conditionMapper::toConditionResponse);
        }
        Set<UUID> ids = content.stream()
                .map(Condition::getId)
                .collect(Collectors.toSet());
        List<NutritionRule> rules = nutritionRuleRepository.findByActiveTrueAndCondition_IdIn(ids);
        Map<UUID, List<NutritionRule>> rulesByCondition = rules.stream()
                .collect(Collectors.groupingBy(nr -> nr.getCondition().getId()));
        return slice.map(a -> conditionMapper.toConditionResponse(a, rulesByCondition, nutritionRuleMapper));
    }

    // Tìm một bệnh nền theo id
    @Override
    public ConditionResponse getById(UUID id) {
        Condition c = conditionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONDITION_NOT_FOUND));
        Set<UUID> ids = Set.of(c.getId());
        List<NutritionRule> rules = nutritionRuleRepository.findByActiveTrueAndCondition_IdIn(ids);
        Map<UUID, List<NutritionRule>> rulesByCondition = rules.stream()
                .collect(Collectors.groupingBy(nr -> nr.getCondition().getId()));
        return conditionMapper.toConditionResponse(c, rulesByCondition, nutritionRuleMapper);
    }

    // Tìm kiếm bệnh nền theo tên
    @Override
    public Slice<ConditionResponse> searchByName(String name, Pageable pageable) {
        String q = name == null ? "" : name.trim();
        Slice<Condition> slice = conditionRepository.findByNameContainingIgnoreCase(q, pageable);
        return slice.map(conditionMapper::toConditionResponse);
    }

    // Đếm toàn bộ bệnh nền
    @Override
    public long getTotalConditions() {
        return conditionRepository.count();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void update(UUID id, ConditionRequest condition) {
        Condition existing = conditionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONDITION_NOT_FOUND));
        if (condition.getName() != null) {
            String newName = condition.getName().trim();
            if (!newName.equalsIgnoreCase(existing.getName().strip())) {
                if (conditionRepository.existsByNameIgnoreCase(newName)) {
                    throw new AppException(ErrorCode.ALLERGY_EXISTED);
                }
                existing.setName(newName);
                conditionRepository.save(existing);
            }
        }
    }
}
