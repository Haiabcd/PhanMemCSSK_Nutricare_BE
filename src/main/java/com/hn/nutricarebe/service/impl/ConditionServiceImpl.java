package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.dto.response.ConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.mapper.ConditionMapper;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.service.ConditionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ConditionServiceImpl implements ConditionService {

    ConditionRepository conditionRepository;
    ConditionMapper conditionMapper;

    @Override
    public List<Condition> findAll() {
        return conditionRepository.findAll();
    }

    @Override
    public ConditionResponse save(ConditionCreationRequest request) {
       if(conditionRepository.existsByName(request.getName())) {
           throw new AppException(ErrorCode.CONDITION_EXISTED);
       }
       Condition saveCondition = conditionRepository.save(conditionMapper.toCondition(request));
       return conditionMapper.toConditionResponse(saveCondition);
    }

    @Override
    public Boolean deleteById(String id) {
        return null;
    }

    @Override
    public Condition findById(String id) {
        return null;
    }
}
