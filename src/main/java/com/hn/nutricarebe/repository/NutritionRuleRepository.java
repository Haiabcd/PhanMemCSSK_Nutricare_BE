package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.NutritionRule;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Modifying
    @Transactional
    @Query("delete from NutritionRule r where r.allergy.id = :allergyId")
    void deleteByAllergyId(@Param("allergyId") UUID allergyId);

    @Modifying
    @Transactional
    @Query("delete from NutritionRule r where r.condition.id = :conditionId")
    void deleteByConditionId(@Param("conditionId") UUID conditionId);

    boolean existsByAllergy_Id(UUID allergyId);
    boolean existsByCondition_Id(UUID conditionId);
}
