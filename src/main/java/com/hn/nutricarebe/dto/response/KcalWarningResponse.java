package com.hn.nutricarebe.dto.response;


import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class KcalWarningResponse {
    String mealSlot;   // bữa ăn (BREAKFAST, LUNCH, DINNER, SNACK)
    double targetKcal; // kcal mục tiêu
    double actualKcal; // kcal thực tế đã ăn
    double diff;       // chênh lệch
    Status status;     // OVER | UNDER | OK

    public enum Status {
        OVER, UNDER, OK
    }
}
