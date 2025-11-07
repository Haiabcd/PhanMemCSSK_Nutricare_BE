package com.hn.nutricarebe.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminLoginResponse {
    String accessToken;
    Long accessExpiresAt;
    String refreshToken;
    Long refreshExpiresAt;
}
