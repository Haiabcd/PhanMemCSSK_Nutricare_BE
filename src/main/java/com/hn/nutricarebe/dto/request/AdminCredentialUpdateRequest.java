package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AdminCredentialUpdateRequest {
    @NotBlank(message = "Tên đăng nhập hiện tại không được để trống")
    String username;
    @NotBlank(message = "Mật khẩu cũ không được để trống")
    String passwordOld;
    String newUsername;
    String newPassword;
}
