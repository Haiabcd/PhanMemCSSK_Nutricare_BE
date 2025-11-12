package com.hn.nutricarebe.dto.request;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import lombok.*;
import lombok.experimental.FieldDefaults;

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
