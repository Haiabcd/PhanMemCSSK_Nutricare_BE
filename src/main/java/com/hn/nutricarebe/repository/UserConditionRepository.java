package com.hn.nutricarebe.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.hn.nutricarebe.entity.UserCondition;

@Repository
public interface UserConditionRepository extends JpaRepository<UserCondition, UUID> {
    @EntityGraph(attributePaths = "condition")
    List<UserCondition> findByUser_Id(UUID userId);

    @Modifying
    @Query("delete from UserCondition uc where uc.user.id = :userId")
    void deleteAllByUserId(UUID userId);

    @Modifying
    @Query("delete from UserCondition uc where uc.user.id = :userId and uc.condition.id in :conditionIds")
    void deleteByUserIdAndConditionIdIn(UUID userId, Collection<UUID> conditionIds);

    @Query(
            """
		SELECT uc.condition.name AS name, COUNT(uc) AS total
		FROM UserCondition uc
		GROUP BY uc.condition.name
		ORDER BY COUNT(uc) DESC
	""")
    List<Object[]> findTopConditionNames();

    boolean existsByCondition_Id(UUID conditionId);
}
