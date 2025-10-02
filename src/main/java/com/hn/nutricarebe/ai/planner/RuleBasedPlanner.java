package com.hn.nutricarebe.ai.planner;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuleBasedPlanner implements Planner{
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*(ngày|day|days)");
    private static final Pattern MEALS = Pattern.compile("(\\d+)\\s*(bữa|meal|meals)");
    private static final Pattern ITEM = Pattern.compile("(\\d+)\\s*(g|gram|grams)?\\s*([\\p{L}\\s]+)");

    // bắt cụm như "tránh hải sản", "không ăn sữa", "ưu tiên ít natri", "giàu đạm", ...
    // đơn giản: tách câu theo dấu phẩy/chấm rồi dò từ khóa

    @Override
    public Plan plan(String userMessage, Map<String,Object> facts) {
        String text = userMessage == null ? "" : userMessage.toLowerCase();


        boolean askEval = text.contains("đánh giá") || text.contains("check") || text.contains("tính kcal");
        if (askEval) {
            var items = parseItems(text); // từ ví dụ "200g ức gà, 150g cơm, 100g rau"
            Plan p = new Plan();
            if (items.isEmpty()) {
                p.setHumanSummary("Bạn hãy gửi dạng: 'đánh giá: 200g ức gà, 150g cơm, 100g rau'.");
                return p;
            }
            var args = new java.util.HashMap<String,Object>();
            args.put("items", items);
            p.addStep("evaluate_meal", args);
            p.setHumanSummary("Mình đã đánh giá bữa ăn của bạn.");
            return p;
        }

        Plan plan = new Plan();
        boolean askPlan = text.contains("kế hoạch") || text.contains("thực đơn") || text.contains("meal plan");
        if (!askPlan) { /* ... như cũ ... */ return plan; }

        int days = extractInt(text, DAYS, 7);
        int mealsPerDayDefault = (facts.get("mealsPerDay") instanceof Number)
                ? ((Number)facts.get("mealsPerDay")).intValue() : 3;
        int mealsPerDay = extractInt(text, MEALS, mealsPerDayDefault);

        ParsedPrefs prefsNew = parsePreferences(text);

        // lấy từ facts cũ
        @SuppressWarnings("unchecked")
        java.util.List<String> focusOld = (java.util.List<String>) facts.getOrDefault("focusTags", java.util.List.of());
        @SuppressWarnings("unchecked")
        java.util.List<String> avoidOld = (java.util.List<String>) facts.getOrDefault("avoidTags", java.util.List.of());

        java.util.LinkedHashSet<String> focus = new java.util.LinkedHashSet<>(focusOld);
        focus.addAll(prefsNew.focusTags);
        java.util.LinkedHashSet<String> avoid = new java.util.LinkedHashSet<>(avoidOld);
        avoid.addAll(prefsNew.avoidTags);

        var args = new java.util.HashMap<String,Object>();
        args.put("days", days);
        args.put("mealsPerDay", mealsPerDay);
        if (!focus.isEmpty()) args.put("focusTags", new java.util.ArrayList<>(focus));
        if (!avoid.isEmpty()) args.put("avoidTags", new java.util.ArrayList<>(avoid));

        plan.addStep("generate_meal_plan", args);

        StringBuilder sb = new StringBuilder("Mình đã tạo kế hoạch ")
                .append(days).append(" ngày, ").append(mealsPerDay).append(" bữa/ngày");
        if (!focus.isEmpty()) sb.append(", ưu tiên ").append(String.join(", ", focus));
        if (!avoid.isEmpty()) sb.append(", tránh ").append(String.join(", ", avoid));
        sb.append(".");
        plan.setHumanSummary(sb.toString());
        return plan;
    }

    private int extractInt(String text, Pattern p, int def) {
        Matcher m = p.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }

    // ====== phân tích sở thích đơn giản ======
    static class ParsedPrefs {
        List<String> focusTags = new ArrayList<>();
        List<String> avoidTags = new ArrayList<>();
        String goal = "";
    }

    private List<Map<String,Object>> parseItems(String text) {
        var parts = text.split("[,;]+");
        var out = new java.util.ArrayList<java.util.Map<String,Object>>();
        for (String p : parts) {
            var m = ITEM.matcher(p.trim());
            if (m.find()) {
                double grams = Double.parseDouble(m.group(1));
                String food = m.group(3).trim();
                out.add(java.util.Map.of("foodName", food, "grams", grams));
            }
        }
        return out;
    }

    private ParsedPrefs parsePreferences(String text) {
        ParsedPrefs out = new ParsedPrefs();

        // mục tiêu (goal) → gợi ý focusTags
        for (var e : TagLexicon.GOAL_TO_FOCUS_TAGS.entrySet()) {
            if (text.contains(e.getKey())) {
                out.goal = e.getKey();
                out.focusTags.addAll(e.getValue());
            }
        }

        // tách các cụm bởi dấu phẩy/chấm
        String[] chunks = text.split("[,.;]+");
        for (String chunk : chunks) {
            String s = chunk.trim();

            boolean isAvoid = TagLexicon.AVOID_HINTS.stream().anyMatch(s::contains);
            boolean isFocus = TagLexicon.FOCUS_HINTS.stream().anyMatch(s::contains);

            // quét từ khóa → tag
            for (var kv : TagLexicon.KEYWORD_TO_TAG.entrySet()) {
                if (s.contains(kv.getKey())) {
                    String tag = kv.getValue();
                    if (isAvoid) {
                        addUnique(out.avoidTags, tag);
                    } else if (isFocus) {
                        addUnique(out.focusTags, tag);
                    } else {
                        // không nói rõ avoid/focus: dựa vào ngữ nghĩa tag
                        if (tag.startsWith("low_")) addUnique(out.focusTags, tag);   // "ít đường"/"ít natri" → focus
                        else addUnique(out.avoidTags, tag); // còn lại (vd sữa/hải sản) → coi như tránh
                    }
                }
            }
        }

        // loại trùng lặp nếu có
        out.focusTags = new ArrayList<>(new LinkedHashSet<>(out.focusTags));
        out.avoidTags = new ArrayList<>(new LinkedHashSet<>(out.avoidTags));
        return out;
    }

    private void addUnique(List<String> list, String v) {
        if (!list.contains(v)) list.add(v);
    }
}
