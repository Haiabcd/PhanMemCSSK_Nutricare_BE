package com.hn.nutricarebe.ai.planner;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class RuleBasedPlanner implements Planner{
    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*(ngày|day|days)");
    private static final Pattern MEALS = Pattern.compile("(\\d+)\\s*(bữa|meal|meals)");

    @Override
    public Plan plan(String userMessage) {
        String text = userMessage == null ? "" : userMessage.toLowerCase();
        Plan plan = new Plan();


        //Nếu có những từ khóa liên quan đến kế hoạch, thực đơn, meal plan ==> askPlan = true
        boolean askPlan = text.contains("kế hoạch") || text.contains("thực đơn") || text.contains("meal plan");
        if (askPlan) {
            int days = extract(text, DAYS, 7);
            int mealsPerDay = extract(text, MEALS, 3);

            Map<String,Object> args = new HashMap<>();
            args.put("days", days);
            args.put("mealsPerDay", mealsPerDay);

            plan.addStep("generate_meal_plan", args);
            plan.setHumanSummary("Mình đã tạo kế hoạch " + days + " ngày, " + mealsPerDay + " bữa/ngày.");
            return plan;
        }
        plan.setHumanSummary("Bạn muốn mình tạo kế hoạch trong mấy ngày (ví dụ: 'kế hoạch 7 ngày, 3 bữa/ngày')?");
        return plan;
    }

    private int extract(String text, Pattern p, int def) {
        var m = p.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : def;
    }
}
