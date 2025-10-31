package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.NutritionRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface NutritionRuleRepository extends JpaRepository<NutritionRule, UUID> {
    @EntityGraph(attributePaths = {"condition", "allergy", "tags"})
    NutritionRule findWithCollectionsById(UUID id);

    @EntityGraph(attributePaths = {"condition", "allergy", "tags"})
    List<NutritionRule> findByActiveTrueAndCondition_IdIn(Set<UUID> conditionIds);

    @EntityGraph(attributePaths = {"condition", "allergy", "tags"})
    List<NutritionRule> findByActiveTrueAndAllergy_IdIn(Set<UUID> allergyIds);
}
