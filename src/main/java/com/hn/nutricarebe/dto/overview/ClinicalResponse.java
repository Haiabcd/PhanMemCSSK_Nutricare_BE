package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.Map;

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
