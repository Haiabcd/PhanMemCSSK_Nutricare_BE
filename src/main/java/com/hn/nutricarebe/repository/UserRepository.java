package com.hn.nutricarebe.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.Role;
import com.hn.nutricarebe.enums.UserStatus;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findTopByDeviceIdAndStatusOrderByCreatedAtDesc(String deviceId, UserStatus status);

    Optional<User> findByProviderUserId(String providerUserId);

    Optional<User> findByUsernameIgnoreCase(String username);

    @Query("select u.createdAt from User u " + "where u.createdAt >= :start and u.createdAt < :end")
    List<Instant> findCreatedAtBetween(@Param("start") Instant start, @Param("end") Instant end);

    // tìm số user được tạo theo ngày trong khoảng thời gian
    @Query("""
		SELECT COUNT(u)
		FROM User u
		WHERE u.createdAt >= :startDate
	""")
    long countUsersCreatedAfter(Instant startDate);

    @Query("""
        SELECT COUNT(u)
        FROM User u
        WHERE u.role <> com.hn.nutricarebe.enums.Role.ADMIN
    """)
    long countNonAdminUsers();

    // đếm số user theo vai trò
    long countByRole(Role role);

    // đếm số user theo trạng thái
    @Query("""
     SELECT COUNT(u)
     FROM User u
     WHERE u.status = :status
       AND u.role <> com.hn.nutricarebe.enums.Role.ADMIN
     """)
    long countByStatus(@Param("status") UserStatus status);
}
