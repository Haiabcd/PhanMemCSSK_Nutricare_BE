package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Food;
import com.hn.nutricarebe.enums.MealSlot;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {
    // Kiểm tra sự tồn tại của món ăn
    @Query("select count(f) > 0 from Food f where lower(f.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);

    // Lấy món ăn theo ID
    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    Optional<Food> findWithCollectionsById(UUID id);

    // Tìm món ăn theo slot
    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    @Query("select distinct f from Food f join f.mealSlots ms where ms = :slot")
    Slice<Food> findByMealSlot(@Param("slot") MealSlot slot, Pageable pageable);


    // Tìm món ăn theo tên gần đúng
    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    Slice<Food> findByNameContainingIgnoreCase(String q, Pageable pageable);


    // Lấy tất cả món ăn
    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    Slice<Food> findAllBy(Pageable pageable);

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

    @Query("SELECT f FROM Food f WHERE LOWER(f.name) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Food> searchByNameContaining(@Param("keyword") String keyword, Pageable pageable);
}
