package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.NutritionRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NutritionRuleRepository extends JpaRepository<NutritionRule, UUID> {
}
