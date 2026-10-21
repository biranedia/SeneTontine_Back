package com.senetontine.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

// Anti faux comptes en masse : 5 inscriptions max par adresse IP et par heure (memoire locale,
// suffisant pour une instance unique).
@Component
public class RegistrationRateLimiter {

    private static final int MAX = 5;
    private static final long WINDOW = 60 * 60 * 1000L;

    private record Window(long start, int count) {}

    private final ConcurrentHashMap<String, Window> byIp = new ConcurrentHashMap<>();

    public void check(HttpServletRequest req) {
        String ip = clientIp(req);
        long now = System.currentTimeMillis();
        Window w = byIp.compute(ip, (k, old) ->
                old == null || now - old.start() > WINDOW ? new Window(now, 1) : new Window(old.start(), old.count() + 1));
        if (w.count() > MAX) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop d'inscriptions depuis ce réseau. Réessaie dans une heure.");
        }
        if (byIp.size() > 10_000) byIp.entrySet().removeIf(e -> now - e.getValue().start() > WINDOW);
    }

    // Render place le client en premiere position de X-Forwarded-For.
    static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
