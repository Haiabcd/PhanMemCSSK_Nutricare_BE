package com.hn.nutricarebe.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SwapCandidate {
    UUID foodId;
    String foodName;
    BigDecimal portion;  // 0.5 / 1.0 / 1.5
    String reason;       // ví dụ: "Tương đương kcal/protein (lệch ~6%/~9%)."
}
