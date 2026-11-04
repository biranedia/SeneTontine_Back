package com.senetontine.config;

import com.senetontine.service.MaintenanceService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Bloque toutes les requêtes non-admin quand le mode maintenance est activé.
 * Les routes /api/admin/** restent accessibles pour que l'admin puisse désactiver la maintenance.
 * /actuator/health reste accessible pour le monitoring externe.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class MaintenanceFilter extends OncePerRequestFilter {

    private final MaintenanceService maintenanceService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!maintenanceService.isEnabled()) {
            chain.doFilter(req, res);
            return;
        }
        String path = req.getRequestURI();
        // Routes toujours accessibles pendant la maintenance
        if (path.startsWith("/api/admin") || path.startsWith("/actuator")) {
            chain.doFilter(req, res);
            return;
        }
        String msg = maintenanceService.getMessage()
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
        res.setStatus(503);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"maintenance\",\"message\":\"" + msg + "\"}");
    }
}
