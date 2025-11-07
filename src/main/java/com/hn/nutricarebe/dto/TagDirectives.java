package com.hn.nutricarebe.dto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagDirectives {
    Set<String> avoid = new HashSet<>();
    Map<String, Double> preferBonus = new HashMap<>();
    Map<String, Double> limitPenalty = new HashMap<>();
}
