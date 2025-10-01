package com.hn.nutricarebe.ai.planner;

import java.util.Map;

public interface Planner {
    Plan plan(String userMessage, Map<String,Object> facts);
}
