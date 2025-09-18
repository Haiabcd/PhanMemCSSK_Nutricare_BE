package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.mapper.ConditionMapper;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.service.ConditionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

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
    public Condition save(ConditionCreationRequest request) {
       if(conditionRepository.existsByName(request.getName())) {
           throw new RuntimeException("Condition with name " + request.getName() + " already exists");
       }
        Condition condition = conditionMapper.toCondition(request);
        return conditionRepository.save(condition);
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
