package com.senetontine.config;

import com.senetontine.model.AuthToken;
import com.senetontine.repository.AuthTokenRepository;
import com.senetontine.service.TokenGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// Authentification par token opaque : le header "Authorization: Bearer <token>" est hashe
// (jamais compare en clair) puis recherche en base. Rejette silencieusement (pas
// d'authentification posee) un token absent, expire ou revoque - Spring Security renvoie
// alors 401/403 plus loin dans la chaine pour les endpoints proteges.
//
// Session glissante : chaque requete authentifiee reussie repousse expiresAt de TOKEN_TTL_DAYS
// (l'utilisateur actif n'est jamais deconnecte en plein usage), mais jamais au-dela de
// createdAt + MAX_ABSOLUTE_TTL_DAYS (force une reconnexion periodique, meme pour un usage
// intensif - limite la fenetre d'exploitation d'un token vole).
@Component
@RequiredArgsConstructor
public class TokenAuthFilter extends OncePerRequestFilter {

    static final long TOKEN_TTL_DAYS = 7;
    static final long MAX_ABSOLUTE_TTL_DAYS = 30;

    private final AuthTokenRepository authTokenRepository;
    private final TokenGenerator tokenGenerator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String rawToken = header.substring(7);
            String hash = tokenGenerator.hash(rawToken);

            Optional<AuthToken> tokenOpt = authTokenRepository.findByTokenHash(hash)
                    .filter(t -> !t.isRevoked())
                    .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                    // Compte suspendu APRES l'emission de ce token (voir AdminService.setActive) :
                    // sans cette verification ici, un compte bloque resterait pleinement
                    // utilisable jusqu'a l'expiration naturelle du token (jusqu'a 30 jours) -
                    // seul le LOGIN etait bloque, pas les sessions deja ouvertes.
                    .filter(t -> t.getUser().isActive());

            tokenOpt.ifPresent(token -> {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + token.getUser().getRole().name()));
                var auth = new UsernamePasswordAuthenticationToken(token.getUser(), null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);

                Instant now = Instant.now();
                Instant slidingExpiry = now.plus(TOKEN_TTL_DAYS, java.time.temporal.ChronoUnit.DAYS);
                Instant absoluteCap = token.getCreatedAt().plus(MAX_ABSOLUTE_TTL_DAYS, java.time.temporal.ChronoUnit.DAYS);
                token.setLastUsedAt(now);
                token.setExpiresAt(slidingExpiry.isBefore(absoluteCap) ? slidingExpiry : absoluteCap);
                authTokenRepository.save(token);
            });
        }
        chain.doFilter(request, response);
    }
}
