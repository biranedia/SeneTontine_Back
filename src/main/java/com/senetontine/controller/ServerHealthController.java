package com.senetontine.controller;

import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.service.MaintenanceService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

/**
 * Supervision du serveur : santé, métriques DB, mode maintenance, redéploiement.
 * Toutes ces routes sont réservées aux administrateurs.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class ServerHealthController {

    private final MaintenanceService maintenanceService;
    private final DataSource dataSource;
    private final Flyway flyway;

    @Value("${render.deploy-hook-url:}")
    private String renderDeployHookUrl;

    private static final Instant SERVER_START = Instant.now();

    private void requireAdmin(User user) {
        if (user == null || user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
    }

    @GetMapping("/server-health")
    public Map<String, Object> serverHealth(@AuthenticationPrincipal User caller) {
        requireAdmin(caller);

        long uptimeSeconds = Duration.between(SERVER_START, Instant.now()).toSeconds();

        // Métriques HikariCP
        int poolActive = 0, poolIdle = 0, poolMax = 0, poolPending = 0;
        boolean dbUp = false;
        if (dataSource instanceof HikariDataSource hds) {
            HikariPoolMXBean pool = hds.getHikariPoolMXBean();
            if (pool != null) {
                poolActive = pool.getActiveConnections();
                poolIdle = pool.getIdleConnections();
                poolMax = hds.getMaximumPoolSize();
                poolPending = pool.getThreadsAwaitingConnection();
                dbUp = true;
            }
        }

        // Dernière migration Flyway
        String lastMigration = "—";
        try {
            var info = flyway.info();
            var applied = Arrays.stream(info.applied())
                    .filter(m -> m.getVersion() != null)
                    .reduce((a, b) -> b) // last
                    .orElse(null);
            if (applied != null) {
                lastMigration = applied.getVersion().getVersion() + " — " + applied.getDescription();
            }
        } catch (Exception ignored) {}

        // JVM heap
        Runtime rt = Runtime.getRuntime();
        long heapUsedMb = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long heapMaxMb  = rt.maxMemory() / (1024 * 1024);

        return Map.ofEntries(
                Map.entry("serverStartedAt", SERVER_START.toString()),
                Map.entry("uptimeSeconds", uptimeSeconds),
                Map.entry("dbUp", dbUp),
                Map.entry("dbPoolActive", poolActive),
                Map.entry("dbPoolIdle", poolIdle),
                Map.entry("dbPoolMax", poolMax),
                Map.entry("dbPoolPending", poolPending),
                Map.entry("lastMigration", lastMigration),
                Map.entry("heapUsedMb", heapUsedMb),
                Map.entry("heapMaxMb", heapMaxMb),
                Map.entry("maintenanceEnabled", maintenanceService.isEnabled()),
                Map.entry("maintenanceMessage", maintenanceService.getMessage()),
                Map.entry("deployHookConfigured", !renderDeployHookUrl.isBlank())
        );
    }

    // ---- Mode maintenance ----

    public record MaintenanceRequest(boolean enabled, String message) {}

    @PostMapping("/maintenance")
    public Map<String, Object> setMaintenance(
            @AuthenticationPrincipal User caller,
            @RequestBody MaintenanceRequest req) {
        requireAdmin(caller);
        maintenanceService.setEnabled(req.enabled());
        if (req.message() != null) maintenanceService.setMessage(req.message());
        return Map.of(
                "enabled", maintenanceService.isEnabled(),
                "message", maintenanceService.getMessage()
        );
    }

    // ---- Redéploiement via Render Deploy Hook ----

    @PostMapping("/redeploy")
    public ResponseEntity<Map<String, Object>> redeploy(@AuthenticationPrincipal User caller) {
        requireAdmin(caller);
        if (renderDeployHookUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RENDER_DEPLOY_HOOK_URL non configuré sur ce serveur.");
        }
        try {
            var client = HttpClient.newHttpClient();
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(renderDeployHookUrl))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(Duration.ofSeconds(10))
                    .build();
            var resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return ResponseEntity.ok(Map.of("triggered", true));
            }
            return ResponseEntity.status(502).body(Map.of(
                    "triggered", false,
                    "renderStatus", resp.statusCode()
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Impossible de joindre Render : " + e.getMessage());
        }
    }
}
