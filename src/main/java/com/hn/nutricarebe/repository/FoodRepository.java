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

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {
    @Query("select count(f) > 0 from Food f where lower(f.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);

    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    Optional<Food> findWithCollectionsById(UUID id);

    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    @Query("select distinct f from Food f join f.mealSlots ms where ms = :slot")
    Slice<Food> findByMealSlot(@Param("slot") MealSlot slot, Pageable pageable);

    @EntityGraph(attributePaths = {"mealSlots", "tags"})
    Slice<Food> findByNameContainingIgnoreCase(String q, Pageable pageable);
}
