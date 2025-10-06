package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    boolean existsByUserId(UUID userId);
    Optional<Profile> findByUser_Id(UUID userId);
}
