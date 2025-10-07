package com.hn.nutricarebe.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class LoginProfile {
    String providerUserId;
    String email;
    String name;
    String avatar;
    boolean emailVerified;
}
