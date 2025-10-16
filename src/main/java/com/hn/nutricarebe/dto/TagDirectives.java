package com.hn.nutricarebe.dto;

import com.hn.nutricarebe.enums.FoodTag;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TagDirectives {
    Set<FoodTag> avoid = new HashSet<>();
    Map<FoodTag, Double> preferBonus = new HashMap<>();
    Map<FoodTag, Double> limitPenalty = new HashMap<>();
}
