package com.senetontine.service;

import com.senetontine.dto.AuthDtos.AuthResponse;
import com.senetontine.dto.AuthDtos.LoginRequest;
import com.senetontine.dto.AuthDtos.RegisterRequest;
import com.senetontine.model.AuditAction;
import com.senetontine.model.AuthToken;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.repository.AuthTokenRepository;
import com.senetontine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long TOKEN_TTL_DAYS = 7;
    private static final int MAX_ACTIVE_SESSIONS_PER_USER = 5;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditLogService auditLogService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByPhone(req.phone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un compte existe deja avec ce numero");
        }
        User user = new User();
        user.setFullName(req.fullName());
        user.setPhone(req.phone());
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setTosAcceptedAt(Instant.now());
        userRepository.save(user);

        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req, String actorIp) {
        // Verification par IP d'abord (credential stuffing), puis par numero (brute-force).
        loginRateLimiter.checkAllowedFromIp(actorIp);
        loginRateLimiter.checkAllowed(req.phone());

        User user = userRepository.findByPhone(req.phone()).orElse(null);

        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(req.phone(), actorIp);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Numero ou mot de passe incorrect");
        }

        if (!user.isActive()) {
            loginRateLimiter.reset(req.phone());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte a été suspendu. Contacte le support.");
        }

        loginRateLimiter.reset(req.phone());

        // Toute connexion reussie d'un compte ADMIN est tracee : permet de detecter un
        // acces suspect (IP inconnue, heure inhabituelle) ou une compromission de compte.
        if (user.getRole() == UserRole.ADMIN) {
            auditLogService.log(user, AuditAction.ADMIN_LOGIN, "User", user.getId(),
                    "Connexion admin", actorIp);
        }

        return issueToken(user);
    }

    @Transactional
    public void logout(String rawToken) {
        String hash = tokenGenerator.hash(rawToken);
        authTokenRepository.findByTokenHash(hash).ifPresent(t -> {
            t.setRevoked(true);
            authTokenRepository.save(t);
        });
    }

    @Transactional
    public void logoutOtherSessions(User currentUser, String currentRawToken) {
        String currentHash = tokenGenerator.hash(currentRawToken);
        authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(currentUser.getId()).stream()
                .filter(t -> !t.getTokenHash().equals(currentHash))
                .forEach(t -> {
                    t.setRevoked(true);
                    authTokenRepository.save(t);
                });
    }

    private AuthResponse issueToken(User user) {
        enforceSessionCap(user);

        String rawToken = tokenGenerator.generateToken();

        AuthToken token = new AuthToken();
        token.setUser(user);
        token.setTokenHash(tokenGenerator.hash(rawToken));
        token.setExpiresAt(Instant.now().plus(TOKEN_TTL_DAYS, ChronoUnit.DAYS));
        authTokenRepository.save(token);

        return new AuthResponse(user.getId(), user.getFullName(), user.getPhone(), rawToken,
                user.getRole().name());
    }

    private void enforceSessionCap(User user) {
        var activeSessions = authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(user.getId());
        int toRevoke = activeSessions.size() - (MAX_ACTIVE_SESSIONS_PER_USER - 1);
        for (int i = 0; i < toRevoke; i++) {
            AuthToken oldest = activeSessions.get(i);
            oldest.setRevoked(true);
            authTokenRepository.save(oldest);
        }
    }
}
