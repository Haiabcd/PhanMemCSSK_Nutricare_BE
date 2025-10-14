package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;

public interface WaterLogService {
    void create(WaterLogCreationRequest request);
}
