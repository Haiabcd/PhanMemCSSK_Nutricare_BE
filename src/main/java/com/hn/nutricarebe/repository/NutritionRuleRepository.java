package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.NutritionRule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface NutritionRuleRepository extends JpaRepository<NutritionRule, UUID> {

    @Query("""
        select r from NutritionRule r
        where r.active = true
          and (
              (:conditionIdsIsEmpty = true or r.condition.id in :conditionIds)
              or
              (:allergyIdsIsEmpty = true or r.allergy.id in :allergyIds)
          )
    """)
    List<NutritionRule> findActiveByConditionsOrAllergies(
            @Param("conditionIds") Set<UUID> conditionIds,
            @Param("allergyIds") Set<UUID> allergyIds,
            @Param("conditionIdsIsEmpty") boolean conditionIdsIsEmpty,
            @Param("allergyIdsIsEmpty") boolean allergyIdsIsEmpty
    );
           
    @EntityGraph(attributePaths = {"condition", "allergy", "foodTags"})
    NutritionRule findWithCollectionsById(UUID id);
}
