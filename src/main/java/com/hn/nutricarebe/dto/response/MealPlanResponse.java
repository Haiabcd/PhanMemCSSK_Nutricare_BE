package com.hn.nutricarebe.dto.response;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MealPlanResponse {
    UUID id;
    UUID user;
    LocalDate date;
    NutritionResponse targetNutrition;
    Integer waterTargetMl;
    Set<MealPlanItemResponse> items;
}
