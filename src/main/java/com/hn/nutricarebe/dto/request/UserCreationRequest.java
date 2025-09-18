package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.enums.Provider;
import com.hn.nutricarebe.enums.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserCreationRequest {
    @NotNull(message = "Provider là bắt buộc")
    Provider provider;
    String deviceId;

}
