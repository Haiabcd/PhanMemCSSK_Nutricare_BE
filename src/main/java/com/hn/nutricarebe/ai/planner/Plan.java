package com.hn.nutricarebe.ai.planner;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class Plan {
    List<Step> steps = new ArrayList<>();  // Các bước trong kế hoạch
    String humanSummary;   //Cách trình bày kế hoạch cho con người

    public void addStep(String name, java.util.Map<String,Object> args) {
        Step s = new Step(); s.name = name; s.args = args; steps.add(s);
    }
}
