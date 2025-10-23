package com.hn.nutricarebe.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DailyNutritionDto {
    private LocalDate date;
    private BigDecimal proteinG;
    private BigDecimal carbG;
    private BigDecimal fatG;
    private BigDecimal fiberG;
}
