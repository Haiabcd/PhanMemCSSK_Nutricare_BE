package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.UserConditionResponse;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.entity.UserCondition;
import com.hn.nutricarebe.repository.UserConditionRepository;
import com.hn.nutricarebe.service.UserConditionService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.mapstruct.Condition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserConditionServiceImpl implements UserConditionService {

    UserConditionRepository userConditionRepository;



//    @Override
//    public List<UserConditionResponse> addUserConditionsToUser(UserConditionCreationRequest request) {
//        User u = request.getUser();
//        Set<UUID> conditionIds = request.getConditionIds();
//        for(UUID conditionId : conditionIds) {
//            UserCondition uc = UserCondition.builder()
//                        .user(u)
//                        .condition(conditionId)
//                        .build();
////            userConditionRepository.addUserConditionToUser(u.getId(), conditionId);
//        }
//    }
}
