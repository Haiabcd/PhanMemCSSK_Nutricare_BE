package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.UserCondition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserConditionRepository extends JpaRepository<UserCondition, UUID> {
    boolean existsByUser_IdAndCondition_Id(UUID userId, UUID conditionId);
}
