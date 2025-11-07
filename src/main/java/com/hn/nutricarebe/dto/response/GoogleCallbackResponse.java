package com.hn.nutricarebe.dto.response;

import com.hn.nutricarebe.enums.AuthFlowOutcome;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class GoogleCallbackResponse {
    AuthFlowOutcome outcome;
    TokenPairResponse tokenResponse;
}
