package com.hn.nutricarebe.service;

import java.util.List;
import java.util.Set;

public interface TagService {
    String getTagVocabularyText();
    List<String> findNameCodeByNormal(Set<String> normalized);
}
