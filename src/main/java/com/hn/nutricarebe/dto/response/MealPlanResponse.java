package com.hn.nutricarebe.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.hn.nutricarebe.entity.User;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MealPlanResponse {
    UUID id;
    User user;
    LocalDate date;
    NutritionResponse targetNutrition;
    Integer waterTargetMl;
}
