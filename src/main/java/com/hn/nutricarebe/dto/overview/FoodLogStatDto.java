package com.hn.nutricarebe.dto.overview;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FoodLogStatDto {
    private String name;
    private Long count;
}
