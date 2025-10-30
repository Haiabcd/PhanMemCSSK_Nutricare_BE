package com.hn.nutricarebe.repository;

import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findTopByDeviceIdAndStatusOrderByCreatedAtDesc(String deviceId, UserStatus status);
    Optional<User> findByProviderUserId(String providerUserId);
    Optional<User> findByUsernameIgnoreCase(String username);
}
