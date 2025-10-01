package com.hn.nutricarebe.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MealEvaluationDto {
     Double kcal;
     Double protein;
     Double carbs;
     Double fat;
     Double fiber;     // nếu bạn có
     List<String> warnings; // vi phạm rule/allergy/condition
}
