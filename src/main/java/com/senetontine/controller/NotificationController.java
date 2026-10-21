package com.senetontine.controller;

import com.senetontine.dto.NotificationDtos.NotificationInfo;
import com.senetontine.dto.NotificationDtos.UnreadCount;
import com.senetontine.model.User;
import com.senetontine.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public List<NotificationInfo> list(@AuthenticationPrincipal User user) {
        return notificationService.list(user);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount(@AuthenticationPrincipal User user) {
        return new UnreadCount(notificationService.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        notificationService.markRead(user, id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal User user) {
        notificationService.markAllRead(user);
    }
}
