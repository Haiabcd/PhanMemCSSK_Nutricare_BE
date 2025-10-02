package com.hn.nutricarebe.ai.planner;

import java.util.List;
import java.util.Map;

public class TagLexicon {
    // Map từ khóa → tag chuẩn trong hệ thống (khớp với FoodTagMap/NutritionRule của bạn)
    public static final Map<String, String> KEYWORD_TO_TAG = Map.ofEntries(
            // nhóm avoid (dị ứng/nhóm thực phẩm)
            Map.entry("hải sản", "shellfish"),
            Map.entry("tôm", "shellfish"),
            Map.entry("cua", "shellfish"),
            Map.entry("mực", "shellfish"),
            Map.entry("sữa", "dairy"),
            Map.entry("bơ sữa", "dairy"),
            Map.entry("gluten", "gluten"),
            Map.entry("đường", "low_sugar"),     // khi user nói "ít đường" → focus tag "low_sugar"
            Map.entry("natri", "low_sodium"),
            Map.entry("muối", "low_sodium"),

            // nhóm focus (mục tiêu dinh dưỡng)
            Map.entry("giàu đạm", "high_protein"),
            Map.entry("nhiều đạm", "high_protein"),
            Map.entry("tăng cơ", "high_protein"),
            Map.entry("giảm mỡ", "low_carb"),
            Map.entry("ít carb", "low_carb"),
            Map.entry("ít chất béo", "low_fat"),
            Map.entry("nhiều chất xơ", "high_fiber")
    );

    // Mục tiêu → gợi ý tag ưu tiên
    public static final Map<String, List<String>> GOAL_TO_FOCUS_TAGS = Map.of(
            "tăng cơ", List.of("high_protein"),
            "giảm mỡ", List.of("low_carb", "high_protein"),
            "giữ cân", List.of("balanced")
    );

    // Từ khóa gợi ý “avoid” (phủ định)
    public static final List<String> AVOID_HINTS = List.of(
            "tránh", "không ăn", "loại", "đừng", "né", "không dùng"
    );

    // Từ khóa gợi ý “focus/ưu tiên”
    public static final List<String> FOCUS_HINTS = List.of(
            "ưu tiên", "tập trung", "nên", "ít", "giàu", "nhiều"
    );
}
