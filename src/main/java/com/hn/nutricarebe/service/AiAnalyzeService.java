package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.AnalyzeByUrlRequest;
import com.hn.nutricarebe.dto.response.FoodAnalyzeResponse;
import com.hn.nutricarebe.service.impl.AiAnalyzeServiceImpl;

public interface AiAnalyzeService {
    FoodAnalyzeResponse analyzeByImage(AnalyzeByUrlRequest req);
}
