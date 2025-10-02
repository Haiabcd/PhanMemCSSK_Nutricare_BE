package com.hn.nutricarebe.ai.tools;

import java.util.Map;



public interface Tool {
    String name();
    Map<String, Object> execute(Map<String, Object> args);
}
