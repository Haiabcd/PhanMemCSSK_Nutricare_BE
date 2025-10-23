package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;
import com.hn.nutricarebe.dto.response.DailyWaterTotalDto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WaterLogService {
    void create(WaterLogCreationRequest request);
    int getTotalAmountByDate(LocalDate date);
    List<DailyWaterTotalDto> getDailyTotals(UUID userId, LocalDate start, LocalDate end, boolean fillMissingDates);
}
