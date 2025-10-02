package com.hn.nutricarebe.ai.tools;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {
    private final Map<String, Tool> tools;

    public ToolRegistry(List<Tool> toolList) {
        this.tools = toolList.stream().collect(Collectors.toMap(Tool::name, t -> t));
    }

    public Map<String, Object> invoke(String name, Map<String, Object> args) {
        Tool t = tools.get(name);
        if (t == null) throw new IllegalArgumentException("Tool not found: " + name);
        return t.execute(args);
    }
}
