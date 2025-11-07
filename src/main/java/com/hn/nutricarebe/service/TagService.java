package com.hn.nutricarebe.service;

import java.util.List;
import java.util.Set;

import com.hn.nutricarebe.dto.request.TagCreationRequest;
import com.hn.nutricarebe.dto.request.TagDto;

public interface TagService {
    String getTagVocabularyText();

    List<String> findNameCodeByNormal(Set<String> normalized);

    List<TagDto> autocomplete(String keyword, int limit);

    void save(TagCreationRequest request);
}
