package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.request.SaveLogRequest;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import java.time.LocalDate;


public interface PlanLogService {
    void savePlanLog(SaveLogRequest req);
    NutritionResponse getNutritionLogByDate(LocalDate date);
    void deletePlanLog(SaveLogRequest req);
}
