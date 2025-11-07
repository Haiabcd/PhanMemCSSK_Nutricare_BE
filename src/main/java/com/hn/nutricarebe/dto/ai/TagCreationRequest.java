package com.hn.nutricarebe.dto.ai;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TagCreationRequest {
    String nameCode;
    String description;
}
