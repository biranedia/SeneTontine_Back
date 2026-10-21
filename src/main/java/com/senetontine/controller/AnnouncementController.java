package com.senetontine.controller;

import com.senetontine.dto.AnnouncementDtos.AnnouncementInfo;
import com.senetontine.dto.AnnouncementDtos.BroadcastRequest;
import com.senetontine.model.User;
import com.senetontine.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    // Ouvert a tout utilisateur connecte (pas de garde admin ici) : c'est precisement le
    // public vise par une annonce plateforme.
    @GetMapping("/api/announcements/latest")
    public Optional<AnnouncementInfo> latest() {
        return announcementService.latest();
    }

    @GetMapping("/api/admin/announcements")
    public List<AnnouncementInfo> history(@AuthenticationPrincipal User caller) {
        return announcementService.history(caller);
    }

    @PostMapping("/api/admin/announcements")
    public AnnouncementInfo broadcast(@AuthenticationPrincipal User caller, @Valid @RequestBody BroadcastRequest req) {
        return announcementService.broadcast(caller, req);
    }
}
