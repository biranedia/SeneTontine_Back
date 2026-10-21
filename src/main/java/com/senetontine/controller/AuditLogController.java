package com.senetontine.controller;

import com.senetontine.dto.AuditLogDtos.AuditLogInfo;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Reserve aux admins plateforme : contrairement aux retraits en attente, l'audit n'est PAS
// filtre par tontine supervisee - un superviseur de tontine n'a pas a voir les actions
// d'administration d'autres tontines que la sienne, et de toute facon les actions qu'il
// effectue lui-meme (approve/reject) sont deja visibles depuis son propre historique de
// retraits. Ce journal global est un outil d'administration plateforme.
@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public List<AuditLogInfo> list(@AuthenticationPrincipal User user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reserve aux administrateurs de la plateforme");
        }
        return auditLogService.list();
    }
}
