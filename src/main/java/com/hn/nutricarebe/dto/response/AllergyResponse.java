package com.hn.nutricarebe.dto.response;

import java.util.List;
import java.util.UUID;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AllergyResponse {
    UUID id;
    String name;
    List<NutritionRuleResponse> nutritionRules;
}
