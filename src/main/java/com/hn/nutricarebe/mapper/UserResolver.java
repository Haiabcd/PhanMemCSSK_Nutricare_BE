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


    public User mustExist(UUID id) {
        if (id == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }
        return userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }
}
