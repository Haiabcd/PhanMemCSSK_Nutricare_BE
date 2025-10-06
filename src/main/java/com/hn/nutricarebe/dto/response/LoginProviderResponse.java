package com.hn.nutricarebe.dto.response;

import com.hn.nutricarebe.entity.User;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class LoginProviderResponse {
    User user;
    String token;
    Boolean isNewUser;
    String name;
    String urlAvatar;
}
