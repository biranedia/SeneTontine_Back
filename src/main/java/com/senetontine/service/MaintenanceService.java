package com.senetontine.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Gère le mode maintenance en mémoire.
 * Quand activé, MaintenanceFilter renvoie 503 pour toutes les routes non-admin.
 * L'état est perdu au redémarrage (volontaire : le serveur revient toujours en marche).
 */
@Service
public class MaintenanceService {

    private final AtomicBoolean enabled = new AtomicBoolean(false);
    private final AtomicReference<String> message = new AtomicReference<>(
            "SèneTontine est temporairement en maintenance. Réessayez dans quelques minutes.");

    public boolean isEnabled() {
        return enabled.get();
    }

    public String getMessage() {
        return message.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public void setMessage(String msg) {
        if (msg != null && !msg.isBlank()) {
            message.set(msg.trim());
        }
    }
}
