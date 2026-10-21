package com.senetontine.service;

import com.senetontine.dto.AuthDtos.AuthResponse;
import com.senetontine.dto.AuthDtos.LoginRequest;
import com.senetontine.dto.AuthDtos.RegisterRequest;
import com.senetontine.model.AuthToken;
import com.senetontine.model.User;
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

    // Duree de vie d'un token a l'emission : ensuite geree en session glissante par
    // TokenAuthFilter (voir TOKEN_TTL_DAYS / MAX_ABSOLUTE_TTL_DAYS la-bas).
    private static final long TOKEN_TTL_DAYS = 7;

    // Nombre de sessions actives simultanees autorisees par utilisateur (un telephone, une
    // tablette, un navigateur...). Au-dela, on revoque la plus ancienne pour eviter qu'un
    // token vole des annees plus tot ne reste indefiniment valide en arriere-plan sans que
    // personne ne s'en rende compte.
    private static final int MAX_ACTIVE_SESSIONS_PER_USER = 5;

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenGenerator tokenGenerator;
    private final LoginRateLimiter loginRateLimiter;

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
        // acceptedTerms est deja garanti `true` ici par @AssertTrue sur RegisterRequest (la
        // requete est rejetee en amont par la validation sinon) - on trace juste l'instant precis
        // du consentement, utile en cas de litige sur les conditions en vigueur a l'inscription.
        user.setTosAcceptedAt(Instant.now());
        userRepository.save(user);

        return issueToken(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // Verifie le quota AVANT toute requete base de donnees : un attaquant qui martele un
        // numero se fait bloquer sans meme qu'on aille lire la table users.
        loginRateLimiter.checkAllowed(req.phone());

        User user = userRepository.findByPhone(req.phone()).orElse(null);

        // meme message et meme comptage d'echec dans les deux cas (numero inconnu ou mauvais
        // mot de passe) : ne jamais laisser deviner si un numero est enregistre sur la plateforme.
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            loginRateLimiter.recordFailure(req.phone());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Numero ou mot de passe incorrect");
        }

        // Verifie APRES l'authentification (mot de passe deja valide) pour ne jamais laisser
        // deviner via le message d'erreur si un numero est simplement suspendu vs un mauvais
        // mot de passe sur un numero inconnu - mais avant d'emettre un token, pour qu'un
        // compte bloque ne puisse jamais en obtenir un nouveau.
        if (!user.isActive()) {
            loginRateLimiter.reset(req.phone());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Ce compte a été suspendu. Contacte le support.");
        }

        loginRateLimiter.reset(req.phone());
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

    // "Deconnecter les autres appareils" : revoque toutes les sessions actives de
    // l'utilisateur SAUF celle utilisee pour l'appel courant. Utile si l'utilisateur pense
    // que son compte est compromis, ou simplement pour faire le menage.
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

        // rawToken n'est renvoye qu'une seule fois, ici, au client. Il n'est jamais
        // recuperable depuis la base ensuite (seul son hash y est stocke).
        return new AuthResponse(user.getId(), user.getFullName(), user.getPhone(), rawToken,
                user.getRole().name());
    }

    // Si l'utilisateur a deja atteint (ou depasse, si on abaisse un jour la limite) le nombre
    // maximal de sessions actives, on revoque les plus anciennes pour ne garder que la place
    // disponible pour la nouvelle connexion. Volontairement silencieux (pas d'erreur cote
    // client) : se reconnecter sur un nouvel appareil ne doit jamais etre bloquant, seulement
    // deconnecter tranquillement le plus vieil appareil inactif.
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
