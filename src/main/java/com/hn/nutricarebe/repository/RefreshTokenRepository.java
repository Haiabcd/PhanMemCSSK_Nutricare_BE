package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {
    Optional<RefreshToken> findById(String jti);
    List<RefreshToken> findByFamilyId(String familyId);
}
