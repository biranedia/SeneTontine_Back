package com.senetontine.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/**
 * Espace admin caché (même principe que SamaSiteWeb) :
 * - ADMIN_PATH défini (12 à 64 caractères) : /api/admin/** répond « introuvable » (404) à tout appel
 *   qui n'envoie pas l'en-tête X-Backoffice égal à cette clé. Personne ne peut savoir que l'API existe.
 * - ADMIN_PATH invalide : espace admin fermé (clé aléatoire) plutôt qu'une adresse devinable.
 * - Non défini : ouvert (développement local), avec un avertissement au démarrage.
 * Le rôle ADMIN reste exigé en plus par AdminService.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Slf4j
public class AdminGateFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Backoffice";

    private final String key;

    public AdminGateFilter(@Value("${app.admin-path:}") String raw) {
        String p = raw == null ? "" : raw.trim().replaceAll("^/+|/+$", "");
        if (p.isEmpty()) {
            key = null;
            log.warn("ADMIN_PATH non défini : l'API admin n'est pas cachée. Définis une adresse secrète en production.");
        } else if (!p.matches("[A-Za-z0-9_-]{12,64}") || p.equalsIgnoreCase("admin")) {
            key = "closed-" + UUID.randomUUID();
            log.error("ADMIN_PATH invalide (12 à 64 caractères : lettres, chiffres, - et _). Espace admin fermé.");
        } else {
            key = p;
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // La requête de pré-vérification CORS (OPTIONS) n'envoie jamais la valeur des en-têtes
        // personnalisés : la bloquer empêchait le navigateur d'appeler l'API admin depuis
        // senetontine.com. Elle ne renvoie aucune donnée, on la laisse passer.
        return !req.getRequestURI().startsWith("/api/admin") || "OPTIONS".equalsIgnoreCase(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (key != null && !allows(req.getHeader(HEADER))) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("Cache-Control", "no-store");
            res.getWriter().write("{\"error\":\"Introuvable\"}");
            return;
        }
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Robots-Tag", "noindex, nofollow");
        chain.doFilter(req, res);
    }

    private boolean allows(String header) {
        return header != null && MessageDigest.isEqual(
                header.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }
}
