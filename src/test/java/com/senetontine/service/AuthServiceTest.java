package com.senetontine.service;

import com.senetontine.dto.AuthDtos.LoginRequest;
import com.senetontine.dto.AuthDtos.RegisterRequest;
import com.senetontine.model.AuthToken;
import com.senetontine.model.User;
import com.senetontine.repository.AuthTokenRepository;
import com.senetontine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthTokenRepository authTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private AuthService authService;
    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final LoginRateLimiter loginRateLimiter = new LoginRateLimiter();

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, authTokenRepository, passwordEncoder, tokenGenerator, loginRateLimiter);
    }

    @Test
    void register_rejectsDuplicatePhone() {
        when(userRepository.existsByPhone("+221770000000")).thenReturn(true);

        var req = new RegisterRequest("Test User", "+221770000000", null, "password123", true);

        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(ResponseStatusException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_hashesPasswordAndIssuesToken() {
        when(userRepository.existsByPhone(any())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed-password");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(authTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var req = new RegisterRequest("Test User", "+221770000000", null, "password123", true);
        var response = authService.register(req);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        // Trace du consentement aux CGU au moment precis de l'inscription (voir V15 et
        // RegisterRequest.acceptedTerms, deja garanti true a ce stade par la validation).
        assertThat(userCaptor.getValue().getTosAcceptedAt()).isNotNull();

        ArgumentCaptor<AuthToken> tokenCaptor = ArgumentCaptor.forClass(AuthToken.class);
        verify(authTokenRepository).save(tokenCaptor.capture());
        // le token stocke en base est le HASH, jamais la valeur renvoyee au client
        assertThat(tokenCaptor.getValue().getTokenHash()).isNotEqualTo(response.token());
        assertThat(tokenCaptor.getValue().getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void login_rejectsUnknownPhone_withGenericMessage() {
        when(userRepository.findByPhone(any())).thenReturn(Optional.empty());

        var req = new LoginRequest("+221770000000", "whatever");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Numero ou mot de passe incorrect");
    }

    @Test
    void login_rejectsWrongPassword_withSameGenericMessageAsUnknownPhone() {
        User user = new User();
        user.setPhone("+221770000000");
        user.setPasswordHash("hashed");
        when(userRepository.findByPhone("+221770000000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        var req = new LoginRequest("+221770000000", "wrong");

        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Numero ou mot de passe incorrect");
    }

    @Test
    void login_blocksAfterTooManyFailedAttempts() {
        User user = new User();
        user.setPhone("+221770000000");
        user.setPasswordHash("hashed");
        when(userRepository.findByPhone("+221770000000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        var req = new LoginRequest("+221770000000", "wrong");

        // 5 tentatives echouees consomment le quota...
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(req)).isInstanceOf(ResponseStatusException.class);
        }

        // ...la 6e est bloquee AVANT meme d'aller verifier le mot de passe.
        assertThatThrownBy(() -> authService.login(req))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Trop de tentatives");

        // le mot de passe n'a ete verifie que pour les 5 premieres tentatives, pas la 6e
        verify(passwordEncoder, times(5)).matches("wrong", "hashed");
    }

    @Test
    void logout_revokesMatchingToken() {
        String rawToken = tokenGenerator.generateToken();
        String hash = tokenGenerator.hash(rawToken);

        AuthToken token = new AuthToken();
        token.setTokenHash(hash);
        token.setRevoked(false);
        when(authTokenRepository.findByTokenHash(hash)).thenReturn(Optional.of(token));

        authService.logout(rawToken);

        assertThat(token.isRevoked()).isTrue();
        verify(authTokenRepository).save(token);
    }

    @Test
    void login_revokesOldestSessionWhenSessionCapReached() {
        User user = new User();
        user.setId(1L);
        user.setPhone("+221770000000");
        user.setPasswordHash("hashed");
        when(userRepository.findByPhone("+221770000000")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("goodpass", "hashed")).thenReturn(true);
        when(authTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 5 sessions deja actives (le maximum autorise) : la plus ancienne doit etre revoquee
        // pour faire de la place a la nouvelle connexion.
        List<AuthToken> existing = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            AuthToken t = new AuthToken();
            t.setId((long) i);
            existing.add(t);
        }
        when(authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(1L)).thenReturn(existing);

        authService.login(new LoginRequest("+221770000000", "goodpass"));

        assertThat(existing.get(0).isRevoked()).isTrue();
        assertThat(existing.subList(1, 5)).allSatisfy(t -> assertThat(t.isRevoked()).isFalse());
    }

    @Test
    void logoutOtherSessions_revokesEverySessionExceptTheCurrentOne() {
        User user = new User();
        user.setId(1L);

        String currentRaw = tokenGenerator.generateToken();
        AuthToken current = new AuthToken();
        current.setTokenHash(tokenGenerator.hash(currentRaw));

        AuthToken other = new AuthToken();
        other.setTokenHash("some-other-hash");

        when(authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(other, current));

        authService.logoutOtherSessions(user, currentRaw);

        assertThat(other.isRevoked()).isTrue();
        assertThat(current.isRevoked()).isFalse();
    }
}
