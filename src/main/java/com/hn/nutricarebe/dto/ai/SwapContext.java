package com.hn.nutricarebe.dto.ai;


import lombok.*;
import lombok.experimental.FieldDefaults;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SwapContext {
    UUID dayId;
    LocalDate date;
    List<MealPlanItemLite> items;
    Set<String> avoidTags;            // từ rule FOOD_TAG scope ITEM (nếu có)
    Set<UUID> recentFoodIds;          // món đã ăn/đã lên kế hoạch 3 ngày gần đây (kể cả hôm nay)
}
