package com.hn.nutricarebe.dto.request;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OnboardingRequest {
    @NotBlank(message = "Mã thiết bị là bắt buộc")
    String deviceId;

    @Valid
    @NotNull(message = "Thông tin profile là bắt buộc")
    ProfileCreationRequest profile;

    @Builder.Default
    Set<UUID> conditions = new HashSet<>();

    @Builder.Default
    Set<UUID> allergies = new HashSet<>();
}
