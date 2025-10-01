package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {
    @Query("select count(f) > 0 from Food f where lower(f.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);


    @Query(value = """
    SELECT f.*
    FROM foods f
    JOIN food_meal_slots ms ON ms.food_id = f.id
    WHERE ms.meal_slot = :slot
      AND f.kcal IS NOT NULL
      AND f.kcal BETWEEN :minKcal AND :maxKcal
    ORDER BY ABS(f.kcal - :perItemTargetKcal) ASC
    LIMIT :limit
    """, nativeQuery = true)
    List<Food> selectCandidatesBySlotAndKcalWindow(
            @Param("slot") String slot,
            @Param("minKcal") int minKcal,
            @Param("maxKcal") int maxKcal,
            @Param("perItemTargetKcal") int perItemTargetKcal,
            @Param("limit") int limit
    );

}
