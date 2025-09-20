package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Food;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FoodRepository extends JpaRepository<Food, UUID> {
    @Query("select count(f) > 0 from Food f where lower(f.name) = lower(:name)")
    boolean existsByNameIgnoreCase(String name);
}
