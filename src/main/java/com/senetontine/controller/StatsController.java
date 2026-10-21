package com.senetontine.controller;

import com.senetontine.dto.StatsDtos.UserStatsInfo;
import com.senetontine.model.User;
import com.senetontine.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    // Scope automatiquement a l'utilisateur authentifie (@AuthenticationPrincipal) - aucun id
    // n'est pris en parametre, donc pas de risque d'IDOR ici (impossible de demander les stats
    // de quelqu'un d'autre).
    @GetMapping("/me")
    public UserStatsInfo myStats(@AuthenticationPrincipal User user) {
        return statsService.getMyStats(user);
    }
}
