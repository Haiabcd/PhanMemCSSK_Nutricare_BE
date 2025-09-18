package com.hn.nutricarebe.mapper;


import com.hn.nutricarebe.dto.request.ConditionCreationRequest;
import com.hn.nutricarebe.entity.Condition;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ConditionMapper {
    Condition toCondition(ConditionCreationRequest request);
}
