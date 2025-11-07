package com.hn.nutricarebe.dto.overview;

import java.util.List;
import java.util.Map;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ClinicalResponse {
    List<Map<String, Object>> top5Condition;
    List<Map<String, Object>> top5Allergy;
    long getTotalAllergies;
    long getTotalConditions;
}
