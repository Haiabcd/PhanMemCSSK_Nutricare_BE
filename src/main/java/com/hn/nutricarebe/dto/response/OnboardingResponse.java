package com.hn.nutricarebe.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OnboardingResponse {
    UserCreationResponse user;
    ProfileCreationResponse profile;
}
