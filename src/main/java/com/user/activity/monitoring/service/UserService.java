package com.user.activity.monitoring.service;

import com.user.activity.monitoring.exception.UserBlockedException;
import com.user.activity.monitoring.exception.UserNotFoundException;
import com.user.activity.monitoring.model.User;
import com.user.activity.monitoring.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(2);
    private final UserRepository userRepository;

    /**
     * Сохранение нового пользователя
     */
    @Transactional
    public User saveUser(User user) {
        log.info("Сохраняем нового пользователя");
        try {
            user.setIsBlocked(false);
            User savedUser = userRepository.save(user);
            log.info("Пользователь успешно сохранен с id: {}", savedUser.getId());
            return savedUser;
        } catch (Exception e) {
            log.error("Ошибка при сохранении пользователя: {}", e.getMessage());
            throw new RuntimeException("Ошибка при сохранении пользователя", e);
        }
    }

    /**
     * Обновление существующего пользователя
     */
    @Transactional
    public User updateUser(User user) {
        log.info("Обновление пользователя с id: {}", user.getId());

        var existUser = userRepository.findById(user.getId());
        if (existUser.isEmpty()) {
            log.error("Пользователь с id {} не найден", user.getId());
            throw new UserNotFoundException(user.getId());
        }

        var existedUser = existUser.get();
        if (isUserBlocked(existedUser)) {
            LocalDateTime unblockTime = existedUser.getBlockedAt().plus(BLOCK_DURATION);
            log.info("Пользователь {} заблокирован до {}", user.getId(), unblockTime);
            throw new UserBlockedException(user.getId(), unblockTime.toString());
        }
        try {
            user.setCreatedAt(existedUser.getCreatedAt());
            user.setIsBlocked(existedUser.getIsBlocked());
            User updatedUser = userRepository.save(user);
            log.info("Пользователь успешно обновлен: {}", updatedUser.getId());
            return updatedUser;
        } catch (Exception e) {
            log.error("Пользователь с id {} не найден", user.getId());
            throw new UserNotFoundException(user.getId());
        }
    }

    /**
     * Удаление пользователя по ID
     */
    @Transactional
    public void deleteUser(Long id) {
        log.info("Удаление пользователя с id: {}", id);
        try {
            userRepository.deleteById(id);
            log.info("Пользователь с id={} успешно удален", id);
        } catch (Exception e) {
            log.error("Ошибка при удалении пользователя: {}", e.getMessage());
            throw new RuntimeException("Ошибка при удалении пользователя", e);
        }
    }

    /**
     * Получение всех пользователей
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        log.debug("Получение всех пользователей");
        try {
            List<User> users = userRepository.findAll();
            log.debug("Найдено {} пользователей", users.size());
            return users;
        } catch (Exception e) {
            log.error("Ошибка поиска пользователей: {}", e.getMessage());
            throw new RuntimeException("Ошибка поиска пользователей", e);
        }
    }

    /**
     * Получение пользователя по ID
     */
    @Transactional(readOnly = true)
    public Optional<User> getUserById(Long id) {
        log.debug("Получение пользователя по id: {}", id);
        try {
            Optional<User> user = userRepository.findById(id);
            if (user.isPresent()) {
                log.debug("Найден пользователь по id: {}", id);
            } else {
                log.error("Пользователь с id {} не найден", id);
                throw new UserNotFoundException(id);
            }
            return user;
        } catch (Exception e) {
            log.error("Ошибка получения пользователя по id: {}", e.getMessage());
            throw new RuntimeException("Ошибка получения пользователя по id", e);
        }
    }

    /**
     * Разблокировка всех пользователей, у которых истекло время блокировки
     */
    @Transactional
    public void unblockExpiredUsers() {
        LocalDateTime now = LocalDateTime.now();

        List<User> expiredBlockedUsers = userRepository.findBlockedUsersWithExpiredTime(now);

        if (expiredBlockedUsers.isEmpty()) {
            log.debug("Нет пользователей для разблокировки");
            return;
        }

        int unblockedCount = 0;

        for (User user : expiredBlockedUsers) {
            try {
                user.setIsBlocked(false);
                user.setBlockedAt(null);
                user.setUpdatedAt(LocalDateTime.now());

                userRepository.save(user);
                unblockedCount++;

                log.info("Пользователь {} разблокирован", user.getId());

            } catch (Exception e) {
                log.error("Ошибка при разблокировке пользователя {}", user.getId(), e);
            }
        }

        log.info("Разблокировано {} пользователей", unblockedCount);
    }

    public boolean isUserBlocked(User user) {
        if (user.getIsBlocked() && user.getBlockedAt() != null) {
            LocalDateTime blockExpiryTime = user.getBlockedAt().plus(BLOCK_DURATION);
            return LocalDateTime.now().isBefore(blockExpiryTime);
        }
        return false;
    }
}
