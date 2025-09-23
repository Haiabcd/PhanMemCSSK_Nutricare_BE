package com.hn.nutricarebe.dto.response;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private LoginUserView user;
    private String supabaseAccessToken;
    private String supabaseRefreshToken;
    private Integer expiresIn;
}
