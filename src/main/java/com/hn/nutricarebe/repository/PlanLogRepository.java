package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.PlanLog;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlanLogRepository extends JpaRepository<PlanLog, UUID> {
    List<PlanLog> findByUser_IdAndDateAndMealSlot(UUID userId, LocalDate date, MealSlot mealSlot);
}
