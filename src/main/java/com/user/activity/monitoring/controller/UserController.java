package com.user.activity.monitoring.controller;

import com.user.activity.monitoring.model.User;
import com.user.activity.monitoring.model.UserActivity;
import com.user.activity.monitoring.service.ActivityStorageService;
import com.user.activity.monitoring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Рест контроллер для вызова апи сервиса.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class UserController {
    private final UserService service;
    private final ActivityStorageService activityStorageService;

    @PostMapping("/users")
    public ResponseEntity<User> createUser(@RequestBody User user) {
        return ResponseEntity.ok(service.saveUser(user));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return ResponseEntity.ok(service.updateUser(user));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        service.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(service.getAllUsers());
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<User> getUserById(@PathVariable Long id) {
        return service.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/stats/actions")
    public ResponseEntity<List<UserActivity>> getRecentActivities(
            @RequestParam(required = false) String action,
            @RequestParam(defaultValue = "20") int limit) {

        List<UserActivity> activities = activityStorageService.getRecentActivities(action, limit);
        return ResponseEntity.ok(activities);
    }

    @GetMapping("/stats/users/{userId}/activities")
    public ResponseEntity<List<UserActivity>> getUserActivities(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "20") int limit) {

        List<UserActivity> activities = activityStorageService.getUserActivities(userId, limit);
        return ResponseEntity.ok(activities);
    }

    @DeleteMapping("/stats/clear")
    public ResponseEntity<Void> clearAll() {
        activityStorageService.cleanupAll();
        return ResponseEntity.ok().build();
    }
}
