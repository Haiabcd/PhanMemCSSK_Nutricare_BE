package com.hn.nutricarebe.dto.request;


import com.hn.nutricarebe.enums.MealSlot;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveLogManualRequest {
    UUID foodId;
    LocalDate date;
    MealSlot mealSlot;
    BigDecimal portion;
}
