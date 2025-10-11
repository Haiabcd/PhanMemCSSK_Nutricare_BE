package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.MealPlanDay;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MealPlanDayRepository extends JpaRepository<MealPlanDay, UUID> {
    @EntityGraph(attributePaths = {
            "items",
            "items.food",
            "items.food.mealSlots",
            "items.food.tags"
    })
    Optional<MealPlanDay> findByUser_IdAndDate(UUID userId, LocalDate date);
}
