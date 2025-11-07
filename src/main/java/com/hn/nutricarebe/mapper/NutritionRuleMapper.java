package com.hn.nutricarebe.mapper;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import com.hn.nutricarebe.dto.response.NutritionRuleResponse;
import com.hn.nutricarebe.entity.NutritionRule;
import com.hn.nutricarebe.entity.Tag;

@Mapper(componentModel = "spring")
public interface NutritionRuleMapper {
    @Mapping(target = "tags", source = "tags", qualifiedByName = "tagsToCodes")
    NutritionRuleResponse toResponse(NutritionRule rule);

    List<NutritionRuleResponse> toResponses(List<NutritionRule> rules);

    @Named("tagsToCodes")
    default List<String> tagsToCodes(Set<Tag> tags) {
        if (tags == null || tags.isEmpty()) return Collections.emptyList();
        return tags.stream()
                .map(Tag::getNameCode)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }
}
