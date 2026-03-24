package com.user.activity.monitoring.repository;

import com.user.activity.monitoring.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Найти всех заблокированных пользователей, у которых истекло время блокировки
     */
    @Query(value = "SELECT * FROM users u WHERE u.is_blocked = true AND u.blocked_at IS NOT NULL AND u.blocked_at + INTERVAL '2 minutes' <= :now",
            nativeQuery = true)
    List<User> findBlockedUsersWithExpiredTime(@Param("now") LocalDateTime now);
}