package com.hn.nutricarebe.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import jakarta.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.UserConditionResponse;
import com.hn.nutricarebe.entity.Condition;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.entity.UserCondition;
import com.hn.nutricarebe.mapper.ConditionMapper;
import com.hn.nutricarebe.repository.ConditionRepository;
import com.hn.nutricarebe.repository.UserConditionRepository;
import com.hn.nutricarebe.service.UserConditionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

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
    public void saveUserCondition(UserConditionCreationRequest request) {
        User u = request.getUser();

        for (UUID conditionId : request.getConditionIds()) {
            conditionRepository.findById(conditionId)
                    .ifPresent(c -> userConditionRepository.save(
                            UserCondition.builder()
                                    .user(u)
                                    .condition(c)
                                    .build()
                    ));
        }
    }


    @Transactional
    @Override
    public boolean updateUserConditions(UUID userId, Set<UUID> conditionIds) {
        Set<UUID> newIds = (conditionIds == null) ? Collections.emptySet() : new LinkedHashSet<>(conditionIds);

        List<UserCondition> current = userConditionRepository.findByUser_Id(userId);
        Set<UUID> currentIds = current.stream()
                .map(uc -> uc.getCondition().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (Objects.equals(currentIds, newIds)) {
            return false;
        }

        if (newIds.isEmpty()) {
            userConditionRepository.deleteAllByUserId(userId);
            return true;
        }

        Set<UUID> toRemove = new LinkedHashSet<>(currentIds);
        toRemove.removeAll(newIds);

        Set<UUID> toAdd = new LinkedHashSet<>(newIds);
        toAdd.removeAll(currentIds);

        if (!toRemove.isEmpty()) {
            userConditionRepository.deleteByUserIdAndConditionIdIn(userId, toRemove);
        }

        if (!toAdd.isEmpty()) {
            List<Condition> addEntities = conditionRepository.findAllById(toAdd);
            List<UserCondition> newLinks = addEntities.stream()
                    .map(c -> UserCondition.builder()
                            .user(User.builder().id(userId).build())
                            .condition(c)
                            .build())
                    .collect(Collectors.toList());
            userConditionRepository.saveAll(newLinks);
        }
        return true;
    }

    @Override
    public List<Map<String, Object>> getTop5ConditionNames() {
        List<Object[]> rows = userConditionRepository.findTopConditionNames();
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < Math.min(rows.size(), 5); i++) {
            Object[] row = rows.get(i);
            String name = (String) row[0];
            Long total = (Long) row[1];
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", name);
            item.put("total", total);
            result.add(item);
        }
        return result;
    }
}
