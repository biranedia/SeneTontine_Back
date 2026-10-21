package com.senetontine.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

// Genere des tokens opaques cryptographiquement forts et calcule leur hash pour stockage.
// SecureRandom (pas Math.random ni UUID.randomUUID reutilise tel quel) : c'est un secret
// d'authentification, il doit etre imprevisible.
@Component
public class TokenGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateToken() {
        byte[] bytes = new byte[32]; // 256 bits d'entropie
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 est garanti disponible sur toute JVM standard ; ne devrait jamais arriver.
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
