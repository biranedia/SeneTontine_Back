package com.senetontine.service;

import com.senetontine.model.*;
import com.senetontine.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Gestion avancée des comptes par l'admin : mot de passe oublié, suppression, modification,
// création offerte, liste des revenus NabooPay. Chaque action est tracée dans le journal d'audit.
@Service
@RequiredArgsConstructor
public class AdminAccountService {

    private static final String ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"; // sans l/1/o/0 ambigus
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final AuthTokenRepository authTokenRepository;
    private final TontineMemberRepository memberRepository;
    private final TontineRepository tontineRepository;
    private final CreatorPaymentRepository creatorPaymentRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public record ResetResult(String temporaryPassword) {}

    public record RevenueItem(Long id, String userName, String userPhone, String purpose, String tontineName,
                              BigDecimal amount, String status, String orderId, Instant createdAt, Instant paidAt,
                              boolean consumed) {}

    private static void requireAdmin(User caller) {
        if (caller == null || caller.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
    }

    private User target(User caller, Long id, boolean allowSelf) {
        requireAdmin(caller);
        User t = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
        if (!allowSelf && t.getId().equals(caller.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible sur ton propre compte");
        }
        return t;
    }

    private void revokeSessions(User u) {
        authTokenRepository.findByUserIdAndRevokedFalseOrderByCreatedAtAsc(u.getId()).forEach(t -> t.setRevoked(true));
    }

    @Transactional
    public ResetResult resetPassword(User caller, Long targetUserId) {
        User target = target(caller, targetUserId, false);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        String temp = sb.toString();
        target.setPasswordHash(passwordEncoder.encode(temp));
        userRepository.save(target);
        revokeSessions(target);
        auditLogService.log(caller, AuditAction.PASSWORD_RESET, "User", target.getId(),
                target.getFullName() + " (" + target.getPhone() + ")");
        return new ResetResult(temp);
    }

    // Suppression par l'admin : le compte est anonymisé (l'historique des tontines reste juste
    // pour les autres membres). Les tontines qu'il a créées et qui ne sont pas terminées sont
    // annulées ; il est retiré des tontines en recrutement.
    @Transactional
    public void deleteUser(User caller, Long targetUserId) {
        User target = target(caller, targetUserId, false);
        if (target.getRole() == UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Retire d'abord ses droits administrateur");
        }
        String label = target.getFullName() + " (" + target.getPhone() + ")";
        for (TontineMember m : memberRepository.findByUserId(target.getId())) {
            Tontine t = m.getTontine();
            boolean open = t.getStatus() == TontineStatus.PENDING || t.getStatus() == TontineStatus.ACTIVE;
            if (m.getRole() == MemberRole.CREATOR && open) {
                t.setStatus(TontineStatus.CANCELLED);
                tontineRepository.save(t);
            } else if (t.getStatus() == TontineStatus.PENDING) {
                memberRepository.delete(m);
            }
        }
        target.setFullName("Utilisateur supprimé");
        target.setPhone("deleted-" + target.getId() + "-" + UUID.randomUUID().toString().substring(0, 8));
        target.setEmail(null);
        target.setAvatarBase64(null);
        target.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        target.setActive(false);
        userRepository.save(target);
        revokeSessions(target);
        auditLogService.log(caller, AuditAction.USER_DELETED, "User", target.getId(), label);
    }

    @Transactional
    public void updateUser(User caller, Long targetUserId, String fullName, String phone) {
        User target = target(caller, targetUserId, true);
        String before = target.getFullName() + " (" + target.getPhone() + ")";
        if (fullName != null && !fullName.isBlank()) target.setFullName(fullName.trim());
        if (phone != null && !phone.isBlank()) {
            String p = phone.replaceAll("\\s", "");
            if (!p.equals(target.getPhone()) && userRepository.existsByPhone(p)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce numéro est déjà utilisé par un autre compte");
            }
            target.setPhone(p);
        }
        userRepository.save(target);
        auditLogService.log(caller, AuditAction.USER_UPDATED, "User", target.getId(),
                before + " → " + target.getFullName() + " (" + target.getPhone() + ")");
    }

    // Offre une création de tontine (geste commercial, partenaire, remboursement...).
    @Transactional
    public void grantCredit(User caller, Long targetUserId) {
        User target = target(caller, targetUserId, true);
        CreatorPayment cp = new CreatorPayment();
        cp.setUser(target);
        cp.setPurpose(CreatorPayment.Purpose.CREATE);
        cp.setAmount(BigDecimal.ZERO);
        cp.setOrderId("GIFT-" + UUID.randomUUID());
        cp.setStatus(CreatorPayment.Status.PAID);
        cp.setPaidAt(Instant.now());
        creatorPaymentRepository.save(cp);
        auditLogService.log(caller, AuditAction.CREDIT_GRANTED, "User", target.getId(),
                "Création de tontine offerte à " + target.getFullName() + " (" + target.getPhone() + ")");
    }

    @Transactional(readOnly = true)
    public List<RevenueItem> revenue(User caller) {
        requireAdmin(caller);
        return creatorPaymentRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "createdAt")).stream()
                .limit(300)
                .map(c -> new RevenueItem(c.getId(), c.getUser().getFullName(), c.getUser().getPhone(),
                        c.getPurpose().name(), c.getTontine() != null ? c.getTontine().getName() : null,
                        c.getAmount(), c.getStatus().name(), c.getOrderId(), c.getCreatedAt(), c.getPaidAt(),
                        c.getConsumedAt() != null))
                .toList();
    }
}
