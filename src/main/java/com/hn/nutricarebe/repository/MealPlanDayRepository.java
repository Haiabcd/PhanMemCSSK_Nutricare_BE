package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.MealPlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface MealPlanDayRepository extends JpaRepository<MealPlanDay, UUID> {
}
