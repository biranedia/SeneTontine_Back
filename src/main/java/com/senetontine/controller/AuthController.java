package com.senetontine.controller;

import com.senetontine.dto.AuthDtos.AuthResponse;
import com.senetontine.dto.AuthDtos.LoginRequest;
import com.senetontine.dto.AuthDtos.RegisterRequest;
import com.senetontine.model.User;
import com.senetontine.service.AuthService;
import com.senetontine.service.LoginRateLimiter;
import com.senetontine.service.RegistrationRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
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
    private final RegistrationRateLimiter registrationRateLimiter;

    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        registrationRateLimiter.check(http);
        return authService.register(req);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        // L'IP est passee au service pour le rate limiting par IP (anti credential-stuffing)
        // et pour tracer les connexions admin dans le journal d'audit.
        return authService.login(req, LoginRateLimiter.clientIp(http));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authHeader) {
        authService.logout(extractToken(authHeader));
    }

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
