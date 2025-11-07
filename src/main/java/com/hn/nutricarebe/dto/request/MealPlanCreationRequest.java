package com.hn.nutricarebe.dto.request;

import java.util.UUID;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MealPlanCreationRequest {
    UUID userId;
    ProfileCreationRequest profile;
}
