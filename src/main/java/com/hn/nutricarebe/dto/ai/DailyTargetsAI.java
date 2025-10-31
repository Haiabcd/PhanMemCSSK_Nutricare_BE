package com.hn.nutricarebe.dto.ai;

import com.hn.nutricarebe.entity.Nutrition;
import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DailyTargetsAI {
   Nutrition targets;
   Integer waterMl;
}
