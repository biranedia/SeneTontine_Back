package com.senetontine.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

// Garde-fou anti brute-force sur /api/auth/login. Deux niveaux de protection :
//
//  1. Par numero (5 echecs / 15 min) : empeche d'attaquer un compte precis par force brute.
//     Meme message d'erreur que le numero soit inconnu ou le mot de passe incorrect :
//     ne jamais laisser deviner si un numero est enregistre sur la plateforme.
//
//  2. Par IP (40 echecs / 15 min) : empeche le credential stuffing (tester des listes de
//     mots de passe sur des milliers de comptes differents depuis la meme IP).
//     Seuil volontairement plus haut que le per-phone pour ne pas punir les reseaux
//     partages (ecole, entreprise, operateur NAT). Reutilise la logique clientIp() de
//     RegistrationRateLimiter (Render place le client en tete de X-Forwarded-For).
//
// Etat garde en memoire, local a ce process : suffisant tant que l'API tourne sur une seule
// instance. Si l'app est un jour deploye en multi-instances, migrer vers Redis.
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS_PHONE = 5;
    private static final int MAX_ATTEMPTS_IP    = 40;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L;

    private static class Window {
        int attempts;
        long windowStartMillis;
    }

    private final ConcurrentHashMap<String, Window> byPhone = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Window> byIp    = new ConcurrentHashMap<>();

    // --- Verifications (a appeler AVANT de tenter l'authentification) ---

    public void checkAllowed(String phone) {
        checkWindow(byPhone, normalize(phone), MAX_ATTEMPTS_PHONE,
                "Trop de tentatives de connexion pour ce numéro. Réessayez dans quelques minutes.");
    }

    public void checkAllowedFromIp(String ip) {
        checkWindow(byIp, ip, MAX_ATTEMPTS_IP,
                "Trop de tentatives de connexion depuis ce réseau. Réessayez dans quelques minutes.");
    }

    // --- Enregistrement des echecs ---

    public void recordFailure(String phone) {
        recordFailure(phone, null);
    }

    public void recordFailure(String phone, String ip) {
        addToWindow(byPhone, normalize(phone));
        if (ip != null) addToWindow(byIp, ip);
    }

    // --- Reset apres connexion reussie ---

    // Reset UNIQUEMENT le compteur par numero (l'utilisateur n'est plus en echec sur son compte).
    // Le compteur par IP N'est PAS reinitialise : un attaquant ne doit pas pouvoir remettre
    // son quota a zero en devinant un seul mot de passe au milieu d'une serie d'attaques.
    public void reset(String phone) {
        byPhone.remove(normalize(phone));
    }

    // --- Utilitaire pour extraire l'IP depuis la requete ---

    public static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    // --- Purge periodique ---

    @Scheduled(fixedRate = 30 * 60 * 1000L)
    void cleanupExpiredWindows() {
        long now = System.currentTimeMillis();
        byPhone.entrySet().removeIf(e -> { synchronized (e.getValue()) { return isExpired(e.getValue(), now); } });
        byIp.entrySet().removeIf(e -> { synchronized (e.getValue()) { return isExpired(e.getValue(), now); } });
    }

    // --- Helpers ---

    private void checkWindow(ConcurrentHashMap<String, Window> map, String key, int max, String message) {
        Window w = map.get(key);
        if (w == null) return;
        synchronized (w) {
            if (isExpired(w, System.currentTimeMillis())) return;
            if (w.attempts >= max) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, message);
            }
        }
    }

    private void addToWindow(ConcurrentHashMap<String, Window> map, String key) {
        Window w = map.computeIfAbsent(key, k -> new Window());
        synchronized (w) {
            long now = System.currentTimeMillis();
            if (isExpired(w, now)) {
                w.attempts = 0;
                w.windowStartMillis = now;
            }
            w.attempts++;
        }
    }

    private boolean isExpired(Window w, long now) {
        return now - w.windowStartMillis > WINDOW_MILLIS;
    }

    // Retire tout sauf les chiffres : "771234567", " 771234567" et "771234567 " sont le meme numero.
    private String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }
}
