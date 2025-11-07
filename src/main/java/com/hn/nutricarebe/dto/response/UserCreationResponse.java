package com.hn.nutricarebe.dto.response;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserCreationResponse {
    UUID id;
    Role role;
    String email;
    Provider provider;
    String providerUserId;
    String deviceId;
    UserStatus status;
}
