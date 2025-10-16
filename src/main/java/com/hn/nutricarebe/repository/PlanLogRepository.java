package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.PlanLog;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface PlanLogRepository extends JpaRepository<PlanLog, UUID> {
    @EntityGraph(attributePaths = {"food"})
    List<PlanLog> findByUser_IdAndDateAndMealSlot(UUID userId, LocalDate date, MealSlot mealSlot);
    List<PlanLog> findByUser_IdAndDate(UUID userId, LocalDate date);

    List<PlanLog> findByUser_IdAndDateBetween(UUID userId, LocalDate start, LocalDate end);
}
