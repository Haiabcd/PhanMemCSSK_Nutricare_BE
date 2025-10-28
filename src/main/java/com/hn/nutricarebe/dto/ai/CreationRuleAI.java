package com.hn.nutricarebe.dto.ai;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreationRuleAI {
    String message;
    UUID conditionId;
    UUID allergyId;
}
