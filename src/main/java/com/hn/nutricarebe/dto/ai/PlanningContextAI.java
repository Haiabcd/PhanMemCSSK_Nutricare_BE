package com.hn.nutricarebe.dto.ai;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PlanningContextAI {

    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FoodsPage {
        List<FoodLite> items;
        Integer limit;
        String nextCursor; // "0", "1", "2"...
        Boolean hasNext;
    }

    @FieldDefaults(level = AccessLevel.PRIVATE)
    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class FoodLite {
        UUID foodId;
        String name;
        List<String> slotHints; // từ Set<MealSlot> -> ["BREAKFAST",...]
        Integer kcal;
        Double proteinG; Double carbG; Double fatG; Double fiberG;
        Integer sodiumMg; Integer sugarMg;
        List<String> tags; // từ Set<Tag> -> nameCode
        Integer defaultServing;
        String servingName;
        Double servingGram;
        Integer cookMinutes;
        String imageKey;
        String description;
    }
}
