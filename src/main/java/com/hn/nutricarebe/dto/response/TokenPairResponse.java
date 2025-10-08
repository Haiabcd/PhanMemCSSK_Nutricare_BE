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
public class TokenPairResponse {
    String tokenType;        // "Bearer"
    String accessToken;
    Long   accessExpiresAt;  // epoch seconds
    String refreshToken;
    Long   refreshExpiresAt; // epoch seconds
}
