package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.response.ProfileDto;
import com.hn.nutricarebe.dto.response.RecoItemDto;

import java.util.List;

public interface RecommendationService {
    List<RecoItemDto> find(int limit);
}
