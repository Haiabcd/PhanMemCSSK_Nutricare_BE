package com.hn.nutricarebe.dto.ai;

import java.util.UUID;

import lombok.*;
import lombok.experimental.FieldDefaults;

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
