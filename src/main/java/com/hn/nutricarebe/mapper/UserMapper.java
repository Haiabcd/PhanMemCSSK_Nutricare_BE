package com.hn.nutricarebe.mapper;


import com.hn.nutricarebe.dto.response.UserCreationResponse;
import com.hn.nutricarebe.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {
    UserCreationResponse toUserCreationResponse(User user);
}
