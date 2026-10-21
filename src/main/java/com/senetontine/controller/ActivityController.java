package com.senetontine.controller;

import com.senetontine.dto.ActivityDtos.ActivityItem;
import com.senetontine.dto.ActivityDtos.UserStats;
import com.senetontine.model.User;
import com.senetontine.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @GetMapping("/history")
    public List<ActivityItem> history(@AuthenticationPrincipal User user) {
        return activityService.history(user.getId());
    }

    @GetMapping("/stats")
    public UserStats stats(@AuthenticationPrincipal User user) {
        return activityService.stats(user.getId());
    }
}
