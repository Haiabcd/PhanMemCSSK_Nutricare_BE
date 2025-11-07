package com.hn.nutricarebe.dto.response;

import java.time.LocalDate;

import com.hn.nutricarebe.entity.Nutrition;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = lombok.AccessLevel.PRIVATE)
public class DayConsumedTotal {
    LocalDate date;
    Nutrition total;
}
