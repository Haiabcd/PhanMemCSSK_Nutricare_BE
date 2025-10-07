package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsByDeviceId(String deviceId);

    Optional<User> findByDeviceId(String deviceId);
    Optional<User> findByProviderUserId(String providerUserId);
    Optional<User> findByEmail(String email);
}
