package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.LogResponse;

import java.time.LocalDate;
import java.util.List;

public interface PlanLogService {
    void savePlanLog(SaveLogRequest req);
    List<LogResponse> getByDate(LocalDate date);
}
