package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;

import java.time.LocalDate;

public interface WaterLogService {
    void create(WaterLogCreationRequest request);
    int getTotalAmountByDate(LocalDate date);
}
