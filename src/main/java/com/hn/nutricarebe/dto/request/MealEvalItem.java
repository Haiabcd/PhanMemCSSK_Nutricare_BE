package com.hn.nutricarebe.dto.request;

import lombok.*;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MealEvalItem {
    String foodName; // hoặc foodId nếu FE gửi id
    Double grams;

}
