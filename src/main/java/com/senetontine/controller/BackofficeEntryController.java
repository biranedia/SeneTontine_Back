package com.senetontine.controller;

import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Donne l'adresse secrete de l'espace admin UNIQUEMENT a un compte admin connecte : l'appli
// affiche alors un bouton « Espace admin » dans le profil. Pour tout autre compte : 404, comme
// si la fonction n'existait pas.
@RestController
public class BackofficeEntryController {

    private final String path;

    public BackofficeEntryController(@Value("${app.admin-path:}") String raw) {
        String p = raw == null ? "" : raw.trim().replaceAll("^/+|/+$", "");
        this.path = p.isEmpty() ? "admin-local" : p;
    }

    @GetMapping("/api/backoffice/entry")
    public ResponseEntity<Map<String, String>> entry(@AuthenticationPrincipal User caller) {
        if (caller == null || caller.getRole() != UserRole.ADMIN || !caller.isActive()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(Map.of("path", path));
    }
}
