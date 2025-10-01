package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ConditionMapper;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.service.ConditionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConditionServiceImpl implements ConditionService {

    ConditionRepository conditionRepository;
    ConditionMapper conditionMapper;

    // Tạo mới một bệnh nền
    @Override
    public ConditionResponse save(ConditionCreationRequest request) {
       if(conditionRepository.existsByName(request.getName())) {
           throw new AppException(ErrorCode.CONDITION_EXISTED);
       }
       Condition saveCondition = conditionRepository.save(conditionMapper.toCondition(request));
       return conditionMapper.toConditionResponse(saveCondition);
    }

    // Xóa một bệnh nền theo id
    @Override
    @Transactional
    public void deleteById(UUID id) {
        Condition con = conditionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.FOOD_NOT_FOUND));
        conditionRepository.delete(con);
    }

    // Lấy danh sách tất cả bệnh nền
    @Override
    public Slice<ConditionResponse> getAll(Pageable pageable) {
        Slice<Condition> conditions = conditionRepository.findAllBy(pageable);
        return conditions.map(conditionMapper::toConditionResponse);
    }

    // Lấy thông tin một bệnh nền theo id
    @Override
    public ConditionResponse getById(UUID id) {
        Condition c = conditionRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONDITION_NOT_FOUND));
        return conditionMapper.toConditionResponse(c);
    }

    // Tìm kiếm bệnh nền theo tên
    @Override
    public Slice<ConditionResponse> searchByName(String name, Pageable pageable) {
        String q = name == null ? "" : name.trim();
        Slice<Condition> slice = conditionRepository.findByNameContainingIgnoreCase(q, pageable);
        return slice.map(conditionMapper::toConditionResponse);
    }


}
