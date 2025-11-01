package com.hn.nutricarebe.dto.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.hn.nutricarebe.enums.Unit;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IngredientResponse {
    UUID id;
    String name;
    NutritionResponse per100;
    String imageUrl;
    Set<String> aliases;
    Unit unit;
}
