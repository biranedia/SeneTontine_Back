package com.senetontine.service;

import com.senetontine.model.AuditAction;
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

import java.security.SecureRandom;

// Mot de passe oublie : l'utilisateur contacte le support (WhatsApp), l'admin genere un mot de
// passe temporaire et le lui transmet. Toutes ses sessions sont coupees ; il le change ensuite
// depuis son profil.
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // sans l/1/o/0 ambigus
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public record ResetResult(String temporaryPassword) {}

    @Transactional
    public ResetResult resetPassword(User caller, Long targetUserId) {
        if (caller == null || caller.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        if (target.getId().equals(caller.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change ton propre mot de passe depuis ton profil");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        String temp = sb.toString();

        target.setPasswordHash(passwordEncoder.encode(temp));
        userRepository.save(target);
        authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(target.getId())
                .forEach(t -> t.setRevoked(true));
        auditLogService.log(caller, AuditAction.PASSWORD_RESET, "User", target.getId(),
                target.getFullName() + " (" + target.getPhone() + ")");
        return new ResetResult(temp);
    }
}
