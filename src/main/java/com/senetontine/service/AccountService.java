package com.senetontine.service;

import com.senetontine.model.MemberRole;
import com.senetontine.model.TontineMember;
import com.senetontine.model.TontineStatus;
import com.senetontine.model.User;
import com.senetontine.repository.AuthTokenRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import com.senetontine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Suppression de compte depuis l'app (exigence Google Play). Le compte est ANONYMISE plutot
// que supprime physiquement : ses cotisations passees restent dans l'historique des tontines
// des autres membres (sinon leurs comptes deviendraient incoherents), mais plus aucune donnee
// personnelle (nom, telephone, e-mail, photo) n'est conservee et toute connexion devient
// impossible.
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final TontineMemberRepository memberRepository;
    private final TontineRepository tontineRepository;
    private final AuthTokenRepository authTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void deleteAccount(User user, String password) {
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Mot de passe incorrect");
        }

        List<TontineMember> memberships = memberRepository.findByUserId(user.getId());
        for (TontineMember m : memberships) {
            TontineStatus status = m.getTontine().getStatus();
            if (status == TontineStatus.ACTIVE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Tu participes encore à la tontine \"" + m.getTontine().getName()
                                + "\" en cours. Attends sa fin (ou quitte-la si c'est possible) avant de supprimer ton compte.");
            }
            if (status == TontineStatus.PENDING && m.getRole() == MemberRole.CREATOR
                    && memberRepository.countByTontineId(m.getTontine().getId()) > 1) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Tu as créé la tontine \"" + m.getTontine().getName()
                                + "\" qui a déjà des membres. Contacte le support pour l'annuler avant de supprimer ton compte.");
            }
        }

        // Tontines en recrutement : on se retire (ou on annule celles creees seul).
        for (TontineMember m : memberships) {
            if (m.getTontine().getStatus() != TontineStatus.PENDING) continue;
            if (m.getRole() == MemberRole.CREATOR) {
                m.getTontine().setStatus(TontineStatus.CANCELLED);
                tontineRepository.save(m.getTontine());
            } else {
                memberRepository.delete(m);
            }
        }

        user.setFullName("Utilisateur supprimé");
        user.setPhone("deleted-" + user.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
        user.setEmail(null);
        user.setAvatarBase64(null);
        user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setActive(false);
        userRepository.save(user);

        authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(user.getId()).forEach(t -> {
            t.setRevoked(true);
            authTokenRepository.save(t);
        });
    }
}
