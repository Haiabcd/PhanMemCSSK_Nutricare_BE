package com.hn.nutricarebe.dto.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter @Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupabaseTokenResponse {
    String access_token;
    String token_type;
    Integer expires_in;
    String refresh_token;
    SupabaseUser user;
}
