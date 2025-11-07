package com.hn.nutricarebe.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InfoResponse {
    ProfileCreationResponse profileCreationResponse;
    List<UserConditionResponse> conditions;
    List<UserAllergyResponse> allergies;
    String provider;
}
