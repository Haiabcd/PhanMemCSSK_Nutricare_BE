package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.UserAllergy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserAllergyRepository extends JpaRepository<UserAllergy, UUID> {
    @EntityGraph(attributePaths = "allergy")
    List<UserAllergy> findByUser_Id(UUID userId);

    @Modifying
    @Query("delete from UserAllergy ua where ua.user.id = :userId")
    void deleteAllByUserId(UUID userId);

    @Modifying
    @Query("delete from UserAllergy ua where ua.user.id = :userId and ua.allergy.id in :allergyIds")
    void deleteByUserIdAndAllergyIdIn(UUID userId, Collection<UUID> allergyIds);
}
