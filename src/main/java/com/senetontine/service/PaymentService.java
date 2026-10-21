package com.senetontine.service;

import com.senetontine.dto.PaymentDtos.DeclareRequest;
import com.senetontine.dto.PaymentDtos.PaymentInfo;
import com.senetontine.dto.PaymentDtos.PotPayoutInfo;
import com.senetontine.model.*;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.PotPayoutRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import com.senetontine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Suivi des cotisations SANS circuit d'argent : les membres envoient leur cotisation
// directement au createur (Wave / Orange Money), la declarent ici, et le createur confirme
// l'avoir recue. Quand toutes les cotisations d'un tour sont confirmees, le tour avance et un
// versement de pot (PotPayout) est ouvert : le createur l'envoie au beneficiaire hors de l'app,
// le marque "envoye", et le beneficiaire confirme "recu".
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TontineRepository tontineRepository;
    private final TontineMemberRepository memberRepository;
    private final PotPayoutRepository potPayoutRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    // Bornes du score de confiance (voir User.trustScore) : 5.0 par defaut sans historique,
    // jamais en dessous de 1.0.
    private static final BigDecimal TRUST_SCORE_MAX = new BigDecimal("5.0");
    private static final BigDecimal TRUST_SCORE_MIN = new BigDecimal("1.0");

    @Transactional
    public PaymentInfo declare(Long tontineId, User user, DeclareRequest req) {
        Tontine tontine = findTontine(tontineId);
        requireMember(tontineId, user);
        requireActive(tontine);

        Payment payment = paymentRepository
                .findByTontineIdAndUserIdAndRoundNumber(tontineId, user.getId(), tontine.getCurrentRound())
                .orElseGet(() -> {
                    Payment p = new Payment();
                    p.setTontine(tontine);
                    p.setUser(user);
                    p.setRoundNumber(tontine.getCurrentRound());
                    p.setAmount(tontine.getCotisationAmount());
                    return p;
                });

        if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.LATE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ta cotisation de ce tour est déjà confirmée.");
        }
        if (payment.getStatus() == PaymentStatus.DECLARED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Paiement déjà déclaré : en attente de confirmation du créateur.");
        }

        // Penalite de retard INDICATIVE : l'app ne preleve rien, elle indique au membre le
        // montant a envoyer (cotisation + penalite) et au createur ce qu'il doit recevoir.
        BigDecimal penalty = BigDecimal.ZERO;
        if (tontine.getNextTurnDate() != null) {
            long daysLate = ChronoUnit.DAYS.between(tontine.getNextTurnDate(), LocalDate.now());
            if (daysLate > 0 && tontine.getPenaltyPerDay() != null && tontine.getPenaltyPerDay().signum() > 0) {
                penalty = tontine.getPenaltyPerDay().multiply(BigDecimal.valueOf(daysLate));
            }
        }

        payment.setMethod(req.method());
        payment.setReference(req.reference() == null || req.reference().isBlank() ? null : req.reference().trim());
        payment.setPenaltyAmount(penalty);
        payment.setDeclaredAt(Instant.now());
        payment.setStatus(PaymentStatus.DECLARED);
        paymentRepository.save(payment);

        User creator = findCreator(tontineId);
        if (creator.getId().equals(user.getId())) {
            // Le createur recoit l'argent sur son propre compte : sa declaration vaut confirmation.
            confirmInternal(tontine, payment, creator);
        } else {
            BigDecimal total = payment.getAmount().add(penalty);
            notificationService.notify(creator, NotificationType.PAYMENT_DECLARED,
                    "Paiement à confirmer 💰",
                    user.getFullName() + " déclare t'avoir envoyé " + fcfa(total) + " via " + label(req.method())
                            + " pour \"" + tontine.getName() + "\" (tour " + payment.getRoundNumber() + ")"
                            + (payment.getReference() != null ? ", réf. " + payment.getReference() : "") + ".");
        }
        return toInfo(payment);
    }

    @Transactional
    public PaymentInfo confirm(Long tontineId, Long paymentId, User caller) {
        Tontine tontine = findTontine(tontineId);
        requireCreatorOrAdmin(tontineId, caller);
        Payment payment = findDeclared(tontineId, paymentId);
        confirmInternal(tontine, payment, caller);
        return toInfo(payment);
    }

    // Le createur n'a rien recu (erreur de numero, transfert non abouti...) : la cotisation
    // repasse "a payer" et le membre est prevenu.
    @Transactional
    public PaymentInfo reject(Long tontineId, Long paymentId, User caller) {
        Tontine tontine = findTontine(tontineId);
        requireCreatorOrAdmin(tontineId, caller);
        Payment payment = findDeclared(tontineId, paymentId);

        payment.setStatus(PaymentStatus.DUE);
        payment.setDeclaredAt(null);
        payment.setReference(null);
        paymentRepository.save(payment);

        notificationService.notify(payment.getUser(), NotificationType.PAYMENT_REJECTED,
                "Paiement non reçu ⚠️",
                "Le créateur de \"" + tontine.getName() + "\" n'a pas trouvé ton paiement du tour "
                        + payment.getRoundNumber() + ". Vérifie ton envoi puis déclare-le à nouveau.");
        return toInfo(payment);
    }

    public List<PaymentInfo> history(Long tontineId, User caller) {
        requireMemberOrAdmin(tontineId, caller);
        return paymentRepository.findByTontineIdOrderByRoundNumberAsc(tontineId)
                .stream().map(this::toInfo).toList();
    }

    public List<PotPayoutInfo> payouts(Long tontineId, User caller) {
        requireMemberOrAdmin(tontineId, caller);
        return potPayoutRepository.findByTontineIdOrderByRoundNumberAsc(tontineId)
                .stream().map(this::toPayoutInfo).toList();
    }

    @Transactional
    public PotPayoutInfo markPotSent(Long tontineId, int roundNumber, User caller) {
        requireCreatorOrAdmin(tontineId, caller);
        PotPayout payout = findPayout(tontineId, roundNumber);
        if (payout.getSentAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce pot est déjà marqué comme envoyé.");
        }
        payout.setSentAt(Instant.now());
        potPayoutRepository.save(payout);

        notificationService.notify(payout.getBeneficiary(), NotificationType.POT_SENT,
                "Ton pot a été envoyé 🎉",
                "Le créateur de \"" + payout.getTontine().getName() + "\" t'a envoyé " + fcfa(payout.getAmount())
                        + ". Confirme la réception dans l'app une fois l'argent reçu.");
        return toPayoutInfo(payout);
    }

    @Transactional
    public PotPayoutInfo markPotReceived(Long tontineId, int roundNumber, User caller) {
        PotPayout payout = findPayout(tontineId, roundNumber);
        if (!payout.getBeneficiary().getId().equals(caller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le bénéficiaire de ce tour peut confirmer la réception.");
        }
        if (payout.getReceivedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Réception déjà confirmée.");
        }
        if (payout.getSentAt() == null) {
            payout.setSentAt(Instant.now());
        }
        payout.setReceivedAt(Instant.now());
        potPayoutRepository.save(payout);

        notificationService.notify(findCreator(tontineId), NotificationType.POT_RECEIVED,
                "Pot bien reçu ✅",
                caller.getFullName() + " confirme avoir reçu le pot du tour " + roundNumber
                        + " de \"" + payout.getTontine().getName() + "\".");
        return toPayoutInfo(payout);
    }

    private void confirmInternal(Tontine tontine, Payment payment, User confirmedBy) {
        boolean late = payment.getPenaltyAmount() != null && payment.getPenaltyAmount().signum() > 0;
        payment.setStatus(late ? PaymentStatus.LATE : PaymentStatus.PAID);
        Instant now = Instant.now();
        payment.setConfirmedAt(now);
        payment.setPaidAt(now);
        paymentRepository.save(payment);

        recalculateTrustScore(payment.getUser());

        if (!payment.getUser().getId().equals(confirmedBy.getId())) {
            notificationService.notify(payment.getUser(), NotificationType.PAYMENT_CONFIRMED,
                    "Paiement confirmé ✅",
                    "Ta cotisation du tour " + payment.getRoundNumber() + " de \"" + tontine.getName() + "\" est confirmée.");
        }

        advanceRoundIfComplete(tontine);
    }

    // Toutes les cotisations du tour sont confirmees : on ouvre le versement du pot au
    // beneficiaire du tour, puis on passe au tour suivant (ou on termine la tontine).
    private void advanceRoundIfComplete(Tontine tontine) {
        int round = tontine.getCurrentRound();
        List<TontineMember> members = memberRepository.findByTontineId(tontine.getId());
        List<Payment> confirmed = paymentRepository.findByTontineIdOrderByRoundNumberAsc(tontine.getId()).stream()
                .filter(p -> p.getRoundNumber().equals(round)
                        && (p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.LATE))
                .toList();
        if (members.isEmpty() || confirmed.size() < members.size()) return;

        BigDecimal pot = confirmed.stream()
                .map(p -> p.getAmount().add(p.getPenaltyAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        User creator = findCreator(tontine.getId());
        TontineMember beneficiaryMember = members.stream()
                .filter(m -> Integer.valueOf(round).equals(m.getTurnOrder()))
                .findFirst()
                .orElse(null);

        if (beneficiaryMember != null && potPayoutRepository.findByTontineIdAndRoundNumber(tontine.getId(), round).isEmpty()) {
            User beneficiary = beneficiaryMember.getUser();
            PotPayout payout = new PotPayout();
            payout.setTontine(tontine);
            payout.setRoundNumber(round);
            payout.setBeneficiary(beneficiary);
            payout.setAmount(pot);
            if (beneficiary.getId().equals(creator.getId())) {
                // Le createur est lui-meme beneficiaire : il detient deja le pot.
                payout.setSentAt(Instant.now());
                payout.setReceivedAt(Instant.now());
            }
            potPayoutRepository.save(payout);

            if (!beneficiary.getId().equals(creator.getId())) {
                notificationService.notify(creator, NotificationType.POT_TO_SEND,
                        "Pot à verser 💸",
                        "Toutes les cotisations du tour " + round + " de \"" + tontine.getName() + "\" sont confirmées. "
                                + "Envoie " + fcfa(pot) + " à " + beneficiary.getFullName() + " puis marque le pot comme envoyé.");
            }
        }

        if (round >= tontine.getMembersRequired()) {
            tontine.setStatus(TontineStatus.COMPLETED);
            tontineRepository.save(tontine);
            members.forEach(m -> notificationService.notify(m.getUser(), NotificationType.TONTINE_COMPLETED,
                    "Tontine terminée 🎊",
                    "Tous les tours de \"" + tontine.getName() + "\" sont bouclés. Merci à tous !"));
            return;
        }

        tontine.setCurrentRound(round + 1);
        tontine.setNextTurnDate(LocalDate.now().plusDays(tontine.getCycleDurationDays()));
        tontineRepository.save(tontine);
        members.forEach(m -> notificationService.notify(m.getUser(), NotificationType.ROUND_COMPLETED,
                "Tour " + round + " terminé",
                "Le tour " + (round + 1) + " de \"" + tontine.getName() + "\" commence. Prochaine échéance dans "
                        + tontine.getCycleDurationDays() + " jours."));
    }

    // Score = part des cotisations confirmees A TEMPS parmi toutes celles confirmees, sur 1.0-5.0.
    private void recalculateTrustScore(User user) {
        long onTime = paymentRepository.countByUserIdAndStatus(user.getId(), PaymentStatus.PAID);
        long late = paymentRepository.countByUserIdAndStatus(user.getId(), PaymentStatus.LATE);
        long total = onTime + late;
        BigDecimal score = TRUST_SCORE_MAX;
        if (total > 0) {
            score = TRUST_SCORE_MAX.multiply(BigDecimal.valueOf(onTime))
                    .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
            if (score.compareTo(TRUST_SCORE_MIN) < 0) score = TRUST_SCORE_MIN;
        }
        user.setTrustScore(score);
        userRepository.save(user);
    }

    private Tontine findTontine(Long tontineId) {
        return tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tontine introuvable"));
    }

    private void requireActive(Tontine tontine) {
        switch (tontine.getStatus()) {
            case ACTIVE -> { }
            case PENDING -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La tontine n'a pas encore démarré : elle attend que tous les membres la rejoignent.");
            case CANCELLED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette tontine a été annulée : aucune cotisation n'est plus possible.");
            case COMPLETED -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette tontine est terminée.");
        }
    }

    private void requireMember(Long tontineId, User user) {
        memberRepository.findByTontineIdAndUserId(tontineId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu n'es pas membre de cette tontine"));
    }

    private void requireMemberOrAdmin(Long tontineId, User caller) {
        if (caller.getRole() == UserRole.ADMIN) return;
        requireMember(tontineId, caller);
    }

    private void requireCreatorOrAdmin(Long tontineId, User caller) {
        if (caller.getRole() == UserRole.ADMIN) return;
        boolean isCreator = memberRepository.findByTontineIdAndUserId(tontineId, caller.getId())
                .map(m -> m.getRole() == MemberRole.CREATOR)
                .orElse(false);
        if (!isCreator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé au créateur de la tontine");
        }
    }

    private User findCreator(Long tontineId) {
        return memberRepository.findByTontineId(tontineId).stream()
                .filter(m -> m.getRole() == MemberRole.CREATOR)
                .map(TontineMember::getUser)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Tontine " + tontineId + " sans créateur"));
    }

    private Payment findDeclared(Long tontineId, Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .filter(p -> p.getTontine().getId().equals(tontineId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
        if (payment.getStatus() != PaymentStatus.DECLARED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce paiement n'est pas en attente de confirmation.");
        }
        return payment;
    }

    private PotPayout findPayout(Long tontineId, int roundNumber) {
        return potPayoutRepository.findByTontineIdAndRoundNumber(tontineId, roundNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun pot à verser pour ce tour"));
    }

    private static String label(PaymentMethod m) {
        return switch (m) {
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
            case FREE_MONEY -> "Free Money";
        };
    }

    private static String fcfa(BigDecimal amount) {
        return String.format(java.util.Locale.US, "%,d", amount.longValue()).replace(",", " ") + " FCFA";
    }

    private PaymentInfo toInfo(Payment p) {
        return new PaymentInfo(
                p.getId(), p.getTontine().getId(), p.getUser().getId(), p.getUser().getFullName(),
                p.getRoundNumber(), p.getAmount(),
                p.getMethod() != null ? p.getMethod().name() : null,
                p.getStatus().name(), p.getDeclaredAt(), p.getConfirmedAt(), p.getReference(),
                p.getPenaltyAmount());
    }

    private PotPayoutInfo toPayoutInfo(PotPayout p) {
        return new PotPayoutInfo(p.getId(), p.getRoundNumber(), p.getBeneficiary().getId(),
                p.getBeneficiary().getFullName(), p.getAmount(), p.getSentAt(), p.getReceivedAt());
    }
}
