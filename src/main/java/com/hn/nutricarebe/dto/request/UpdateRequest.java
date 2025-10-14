package com.hn.nutricarebe.dto.request;

import jakarta.validation.Valid;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateRequest {
    @Valid
    ProfileUpdateRequest profile;
    Set<UUID> conditions;
    Set<UUID> allergies;
    LocalDate startDate;
}
