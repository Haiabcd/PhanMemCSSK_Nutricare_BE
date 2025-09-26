package com.hn.nutricarebe.ai.planner;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PUBLIC)
public class Step {
    String name; // ví dụ: "generate_meal_plan"
    Map<String,Object> args = new HashMap<>();
}
