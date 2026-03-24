package com.user.activity.monitoring.scheduler;

import com.user.activity.monitoring.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserUnblockScheduler {
    private final UserService  userService;

    @Scheduled(fixedDelay = 30000)
    public void unblockEvery30Seconds() {
        userService.unblockExpiredUsers();
    }
}
