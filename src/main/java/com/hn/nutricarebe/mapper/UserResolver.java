package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class UserResolver {
    UserRepository userRepository;


    public User getUserByToken() {
        UUID uuid = UUID.fromString("fb2427fd-c1a4-46f9-b75e-cff226c14d5c");
        return userRepository.findById(uuid)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
