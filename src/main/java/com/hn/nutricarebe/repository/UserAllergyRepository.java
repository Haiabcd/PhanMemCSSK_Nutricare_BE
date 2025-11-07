package com.hn.nutricarebe.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.hn.nutricarebe.entity.UserAllergy;

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

    @Query(
            """
		SELECT ua.allergy.name AS name, COUNT(ua) AS total
		FROM UserAllergy ua
		GROUP BY ua.allergy.name
		ORDER BY COUNT(ua) DESC
	""")
    List<Object[]> findTopAllergyNames();

    boolean existsByAllergy_Id(UUID allergyId);
}
