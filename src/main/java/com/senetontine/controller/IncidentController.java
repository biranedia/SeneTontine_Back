package com.senetontine.controller;

import com.senetontine.dto.IncidentDtos.IncidentInfo;
import com.senetontine.dto.IncidentDtos.ResolveIncidentRequest;
import com.senetontine.dto.IncidentDtos.SubmitIncidentRequest;
import com.senetontine.model.IncidentStatus;
import com.senetontine.model.User;
import com.senetontine.service.IncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    // Ouvert a tout utilisateur authentifie (voir IncidentService) - pas de restriction KYC ici.
    @PostMapping("/api/incidents")
    public IncidentInfo submit(@AuthenticationPrincipal User user, @Valid @RequestBody SubmitIncidentRequest req) {
        return incidentService.submit(user, req);
    }

    @GetMapping("/api/incidents/mine")
    public List<IncidentInfo> mine(@AuthenticationPrincipal User user) {
        return incidentService.mine(user);
    }

    // Reserve aux admins (verifie dans IncidentService.requireAdmin, meme pattern que les
    // autres endpoints /api/admin/**).
    @GetMapping("/api/admin/incidents")
    public List<IncidentInfo> listAll(@AuthenticationPrincipal User caller, @RequestParam(required = false) String status) {
        IncidentStatus filter = null;
        if (status != null && !status.isBlank()) {
            try {
                filter = IncidentStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide");
            }
        }
        return incidentService.listAll(caller, filter);
    }

    @PostMapping("/api/admin/incidents/{id}/resolve")
    public IncidentInfo resolve(@AuthenticationPrincipal User caller, @PathVariable Long id,
                                 @Valid @RequestBody ResolveIncidentRequest req) {
        IncidentStatus status;
        try {
            status = IncidentStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide");
        }
        return incidentService.resolve(caller, id, status, req.adminNote());
    }
}
