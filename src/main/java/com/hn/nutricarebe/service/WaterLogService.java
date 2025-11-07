package com.hn.nutricarebe.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.hn.nutricarebe.dto.request.WaterLogCreationRequest;
import com.hn.nutricarebe.dto.response.DailyWaterTotalDto;

public interface WaterLogService {
    void create(WaterLogCreationRequest request);

    int getTotalAmountByDate(LocalDate date);

    List<DailyWaterTotalDto> getDailyTotals(UUID userId, LocalDate start, LocalDate end, boolean fillMissingDates);
}
