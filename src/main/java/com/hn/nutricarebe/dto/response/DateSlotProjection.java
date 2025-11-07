package com.hn.nutricarebe.dto.response;

import java.time.LocalDate;

import com.hn.nutricarebe.enums.MealSlot;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class DateSlotProjection {
    LocalDate date;
    MealSlot mealSlot;
}
