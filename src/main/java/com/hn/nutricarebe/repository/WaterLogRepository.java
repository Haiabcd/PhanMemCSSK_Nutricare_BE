package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.WaterLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface WaterLogRepository extends JpaRepository<WaterLog, UUID> {
    Optional<WaterLog> findByUser_IdAndDate(UUID userId, LocalDate date);

    @Query("SELECT COALESCE(SUM(w.amountMl), 0) " +
            "FROM WaterLog w " +
            "WHERE w.user.id = :userId AND w.date = :date")
    int sumAmountByUserAndDate(@Param("userId") UUID userId,
                               @Param("date") LocalDate date);
}
