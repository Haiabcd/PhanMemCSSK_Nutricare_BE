package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.UserCondition;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

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
}
