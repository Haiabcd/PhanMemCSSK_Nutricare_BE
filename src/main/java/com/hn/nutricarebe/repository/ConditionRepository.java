package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Condition;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface ConditionRepository extends JpaRepository<Condition, UUID> {
    boolean existsByName(String name);
    Slice<Condition> findAllBy(Pageable pageable);
    Slice<Condition> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
