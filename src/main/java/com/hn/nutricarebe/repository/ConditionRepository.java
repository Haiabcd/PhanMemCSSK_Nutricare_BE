package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Condition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, UUID> {
    boolean existsByName(String name);
}
