package com.hn.nutricarebe.dto.request;


import lombok.*;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MealPlanCreationRequest {
    UUID userId;
    ProfileCreationRequest profile;
}
