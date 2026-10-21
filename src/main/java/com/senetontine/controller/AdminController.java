package com.senetontine.controller;

import com.senetontine.dto.AdminDtos.AdminTontineInfo;
import com.senetontine.dto.AdminDtos.AdminUserInfo;
import com.senetontine.dto.AdminDtos.PlatformOverview;
import com.senetontine.dto.AdminDtos.UpdateActiveRequest;
import com.senetontine.dto.AdminDtos.UpdateRoleRequest;
import com.senetontine.dto.AdminDtos.UpdateTontineStatusRequest;
import com.senetontine.dto.AdminDtos.UserDetailInfo;
import com.senetontine.model.TontineStatus;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.service.AdminService;
import com.senetontine.service.AdminAnalyticsService;
import com.senetontine.dto.AnalyticsDtos.Analytics;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Reserve aux comptes ADMIN - verifie dans AdminService.requireAdmin (meme pattern que
// autres controleurs : autorisation geree cote service, pas via un
// hasRole() statique dans SecurityConfig, voir le commentaire de SecurityConfig).
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/analytics")
    public Analytics analytics(@AuthenticationPrincipal User caller, @RequestParam(defaultValue = "30") int days) {
        return adminAnalyticsService.analytics(caller, days);
    }

    @GetMapping("/overview")
    public PlatformOverview overview(@AuthenticationPrincipal User caller) {
        return adminService.getOverview(caller);
    }

    @GetMapping("/users")
    public List<AdminUserInfo> listUsers(@AuthenticationPrincipal User caller, @RequestParam(required = false) String query) {
        return adminService.listUsers(caller, query);
    }

    @GetMapping("/users/{id}/detail")
    public UserDetailInfo userDetail(@AuthenticationPrincipal User caller, @PathVariable Long id) {
        return adminService.getUserDetail(caller, id);
    }

    @PostMapping("/users/{id}/active")
    public AdminUserInfo setActive(@AuthenticationPrincipal User caller, @PathVariable Long id,
                                    @Valid @RequestBody UpdateActiveRequest req) {
        return adminService.setActive(caller, id, req.active());
    }

    @PostMapping("/users/{id}/role")
    public AdminUserInfo setRole(@AuthenticationPrincipal User caller, @PathVariable Long id,
                                  @Valid @RequestBody UpdateRoleRequest req) {
        UserRole role;
        try {
            role = UserRole.valueOf(req.role());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle invalide");
        }
        return adminService.setRole(caller, id, role);
    }

    // Export CSV brut, telechargeable directement depuis le navigateur (Content-Disposition:
    // attachment). Sur l'app native, le front propose une alternative (partage/copie) car un
    // telechargement de fichier fonctionne differemment hors navigateur.
    @GetMapping(value = "/export/users", produces = "text/csv")
    public ResponseEntity<String> exportUsers(@AuthenticationPrincipal User caller) {
        String csv = adminService.exportUsersCsv(caller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=utilisateurs.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping(value = "/export/tontines", produces = "text/csv")
    public ResponseEntity<String> exportTontines(@AuthenticationPrincipal User caller) {
        String csv = adminService.exportTontinesCsv(caller);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tontines.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/tontines")
    public List<AdminTontineInfo> listTontines(@AuthenticationPrincipal User caller) {
        return adminService.listTontines(caller);
    }

    @PostMapping("/tontines/{id}/status")
    public AdminTontineInfo setTontineStatus(@AuthenticationPrincipal User caller, @PathVariable Long id,
                                              @Valid @RequestBody UpdateTontineStatusRequest req) {
        TontineStatus status;
        try {
            status = TontineStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide");
        }
        return adminService.setTontineStatus(caller, id, status);
    }

}
