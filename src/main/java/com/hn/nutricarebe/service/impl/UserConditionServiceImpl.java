package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.UserConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.entity.UserCondition;
import com.hn.nutricarebe.mapper.ConditionMapper;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.repository.UserConditionRepository;
import com.hn.nutricarebe.service.UserConditionService;
import jakarta.transaction.Transactional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserConditionServiceImpl implements UserConditionService {

    UserConditionRepository userConditionRepository;
    ConditionRepository conditionRepository;
    ConditionMapper conditionMapper;

    @Override
    public List<UserConditionResponse> findByUser_Id(UUID userId) {
        return userConditionRepository.findByUser_Id(userId).stream()
                .map(uc -> conditionMapper.toUserConditionResponse(uc.getCondition()))
                .toList();
    }

    @Override
    public List<UserConditionResponse> saveUserCondition(UserConditionCreationRequest request) {
       User u = request.getUser();
       List<UserConditionResponse> response = new ArrayList<>();
       for(UUID conditionId : request.getConditionIds()) {
           Condition c =  conditionRepository.findById(conditionId).orElse(null);
           if(c != null) {
               userConditionRepository.save(UserCondition.builder()
                          .user(u)
                          .condition(c)
                          .build());
               response.add(conditionMapper.toUserConditionResponse(c));
           }
       }
       return response;
    }



    @Transactional
    @Override
    public void updateUserConditions(UUID userId, Set<UUID> conditionIds) {
        if (conditionIds == null || conditionIds.isEmpty()) {
            userConditionRepository.deleteAllByUserId(userId);
            return;
        }

        List<UserCondition> current = userConditionRepository.findByUser_Id(userId);
        Set<UUID> currentIds = current.stream()
                .map(uc -> uc.getCondition().getId())
                .collect(Collectors.toSet());

        Set<UUID> toAdd = new LinkedHashSet<>(conditionIds);
        toAdd.removeAll(currentIds);

        Set<UUID> toRemove = new LinkedHashSet<>(currentIds);
        toRemove.removeAll(conditionIds);

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            return;
        }

        if (!toRemove.isEmpty()) {
            userConditionRepository.deleteByUserIdAndConditionIdIn(userId, toRemove);
        }

        if (!toAdd.isEmpty()) {
            List<Condition> conditionsToAdd = conditionRepository.findAllById(toAdd);
            List<UserCondition> newLinks = conditionsToAdd.stream()
                    .map(c -> UserCondition.builder()
                            .user(User.builder().id(userId).build())
                            .condition(c)
                            .build())
                    .collect(Collectors.toList());
            userConditionRepository.saveAll(newLinks);
        }
    }
}

