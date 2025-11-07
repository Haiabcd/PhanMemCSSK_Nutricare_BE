package com.hn.nutricarebe.mapper;

import org.mapstruct.Mapper;

import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserCreationResponse toUserCreationResponse(User user);
}
