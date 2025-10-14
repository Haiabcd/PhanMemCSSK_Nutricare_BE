package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.WaterLog;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface WaterLogRepository {
    Optional<WaterLog> findByUser_IdAndDate(UUID userId, LocalDate date);
}
