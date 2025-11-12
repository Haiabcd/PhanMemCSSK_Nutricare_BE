package com.hn.nutricarebe.dto.response;

import com.hn.nutricarebe.enums.MealSlot;
import lombok.*;
import lombok.experimental.FieldDefaults;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SwapSuggestion {
    UUID itemId;
    String slot;
    UUID originalFoodId;
    String originalFoodName;
    BigDecimal originalPortion;
    List<SwapCandidate> candidates; // tối đa 3
}
