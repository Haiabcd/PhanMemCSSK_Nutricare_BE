package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.WaterLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {
    Optional<WaterLog> findByUser_IdAndDate(UUID userId, LocalDate date);
}
