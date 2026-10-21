package com.senetontine.controller;

import com.senetontine.dto.AuthDtos.AuthResponse;
import com.senetontine.dto.AuthDtos.LoginRequest;
import com.senetontine.dto.AuthDtos.RegisterRequest;
import com.senetontine.model.User;
import com.senetontine.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }

    // Sans cet endpoint, "se deconnecter" ne faisait qu'effacer le token cote appareil : un
    // token vole ou intercepte avant la deconnexion restait valide en base jusqu'a expiration
    // naturelle. Desormais le token est explicitement revoque cote serveur.
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(extractToken(authHeader));
    }

    // Revoque toutes les sessions actives SAUF celle-ci (utile depuis l'ecran profil :
    // "deconnecter mes autres appareils").
    @PostMapping("/logout-others")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutOthers(@AuthenticationPrincipal User user, @RequestHeader("Authorization") String authHeader) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide ou expire");
        }
        authService.logoutOtherSessions(user, extractToken(authHeader));
    }

    private String extractToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token manquant");
        }
        return authHeader.substring(7);
    }
}
