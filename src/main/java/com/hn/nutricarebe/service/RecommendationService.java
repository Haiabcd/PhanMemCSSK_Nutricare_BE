package com.hn.nutricarebe.service;

import java.util.List;

import com.hn.nutricarebe.dto.response.RecoItemDto;

public interface RecommendationService {
    List<RecoItemDto> find(int limit);
}
