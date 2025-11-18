package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.WeightLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeightLogRepository extends JpaRepository<WeightLog, UUID> {
    Optional<WeightLog> findTopByProfileOrderByLoggedAtDesc(Profile profile);
    List<WeightLog> findByProfile_User_IdAndLoggedAtBetweenOrderByLoggedAt(
            UUID userId, LocalDate start, LocalDate end);
    Optional<WeightLog> findByProfile_IdAndLoggedAt(UUID profileId, LocalDate loggedAt);
}
