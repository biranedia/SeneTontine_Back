package com.senetontine.service;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ConcurrentHashMap;

// Garde-fou anti brute-force sur /api/auth/login. Etat garde en memoire, local a ce process :
// suffisant tant que l'API tourne sur une seule instance. Si un jour l'app est deployee
// derriere plusieurs instances/replicas, cet etat devra migrer vers un magasin partage
// (Redis) pour rester efficace - note laissee ici volontairement plutot que de complexifier
// prematurement une appli qui tourne encore sur une seule instance.
@Component
public class LoginRateLimiter {

    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MILLIS = 15 * 60 * 1000L; // 15 minutes

    private static class Window {
        int attempts;
        long windowStartMillis;
    }

    private final ConcurrentHashMap<String, Window> attemptsByPhone = new ConcurrentHashMap<>();

    // A appeler AVANT de verifier le mot de passe : bloque la tentative si le quota est deja
    // depasse pour ce numero, sans meme toucher a la base.
    public void checkAllowed(String phone) {
        Window w = attemptsByPhone.get(normalize(phone));
        if (w == null) return;
        synchronized (w) {
            if (isWindowExpired(w)) return;
            if (w.attempts >= MAX_ATTEMPTS) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Trop de tentatives de connexion pour ce numéro. Réessayez dans quelques minutes.");
            }
        }
    }

    // A appeler apres un mauvais numero OU un mauvais mot de passe (meme comportement dans
    // les deux cas, pour ne jamais laisser deviner si un numero existe sur la plateforme).
    public void recordFailure(String phone) {
        Window w = attemptsByPhone.computeIfAbsent(normalize(phone), k -> new Window());
        synchronized (w) {
            if (isWindowExpired(w)) {
                w.attempts = 0;
                w.windowStartMillis = System.currentTimeMillis();
            }
            w.attempts++;
        }
    }

    // A appeler apres une connexion reussie, pour ne pas punir un utilisateur legitime qui
    // avait juste fait une faute de frappe avant de retrouver son mot de passe.
    public void reset(String phone) {
        attemptsByPhone.remove(normalize(phone));
    }

    // Sans ca, cette map grossit indefiniment : une entree par numero ayant echoue au moins
    // une fois a se connecter, jamais retiree autrement (reset() ne s'execute qu'en cas de
    // succes). Sur une plateforme avec beaucoup d'utilisateurs sur la duree, c'est une fuite
    // memoire lente. Purge toutes les 30 min les fenetres expirees (plus consultees par
    // checkAllowed() de toute facon, donc sans risque a retirer).
    @Scheduled(fixedRate = 30 * 60 * 1000L)
    void cleanupExpiredWindows() {
        attemptsByPhone.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return isWindowExpired(entry.getValue());
            }
        });
    }

    private boolean isWindowExpired(Window w) {
        return System.currentTimeMillis() - w.windowStartMillis > WINDOW_MILLIS;
    }

    // Retire tout sauf les chiffres (pas juste trim()) : sinon "771234567", " 771234567" et
    // "771234567 " (ou toute variante avec espaces/tirets) comptaient comme des numeros
    // DIFFERENTS pour ce limiteur, chacun avec son propre quota de 5 tentatives - un
    // attaquant pouvait contourner la limite en variant juste le formatage du numero soumis.
    // Toutes les variantes d'un meme numero partagent maintenant le meme compteur.
    private String normalize(String phone) {
        return phone == null ? "" : phone.replaceAll("\\D", "");
    }
}
