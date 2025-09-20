package com.hn.nutricarebe.dto.request;

import com.hn.nutricarebe.entity.User;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserAllergyCreationRequest {
    @NotNull(message = "Người dùng là bắt buộc")
    User user;
    @NotEmpty(message = "Danh sách dị ứng là bắt buộc")
    private Set<@NotNull(message = "Mã dị ứng bắt buộc") UUID> allergyIds;
}
