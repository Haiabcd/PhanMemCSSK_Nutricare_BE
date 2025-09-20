package com.hn.nutricarebe.dto.request;



import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OnboardingRequest {
    @Valid
    @NotNull(message = "Thông tin user là bắt buộc")
    UserCreationRequest user;

    @Valid
    @NotNull(message = "Thông tin profile là bắt buộc")
    ProfileCreationRequest profile;

    @Builder.Default
    Set<UUID> conditions = new HashSet<>();
    @Builder.Default
    Set<UUID> allergies = new HashSet<>();
}
