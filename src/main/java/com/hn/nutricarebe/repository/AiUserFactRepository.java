package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.AiUserFact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiUserFactRepository extends JpaRepository<AiUserFact, Long> {
    List<AiUserFact> findByUserId(UUID userId);
    AiUserFact findByUserIdAndKey(UUID userId, String key);
}
