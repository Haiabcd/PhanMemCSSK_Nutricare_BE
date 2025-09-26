package com.hn.nutricarebe.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PlanRequest {
    String userId;
    int days;
    Integer mealsPerDay;
    List<String> focusTags;
    List<String> avoidTags;
}
