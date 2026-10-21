package com.senetontine.service;

import com.senetontine.dto.BillingDtos.CheckoutResponse;
import com.senetontine.dto.BillingDtos.PaymentStatusInfo;
import com.senetontine.dto.BillingDtos.Quote;
import com.senetontine.model.*;
import com.senetontine.model.CreatorPayment.Purpose;
import com.senetontine.model.CreatorPayment.Status;
import com.senetontine.repository.CreatorPaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

// Paiements createur via NabooPay. Regles de securite :
//  - le prix est fixe cote serveur, jamais transmis par l'app ;
//  - un paiement n'est credite qu'apres avoir REDEMANDE son etat a NabooPay (le webhook,
//    meme signe, et la page "succes" ne sont que des signaux de declenchement) ;
//  - le montant reellement paye est compare au prix attendu ;
//  - verrou pessimiste + statut PENDING : un paiement n'est credite qu'une seule fois, meme
//    si NabooPay renvoie le webhook plusieurs fois en parallele ;
//  - chaque droit (paiement PAID) n'est consomme qu'une fois, a la creation/au renouvellement.
@Slf4j
@Service
@RequiredArgsConstructor
public class BillingService {

    private static final Set<String> PAID_STATUSES = Set.of("paid", "completed", "success", "successful");
    private static final Set<String> FAILED_STATUSES = Set.of("cancelled", "canceled", "failed", "refunded", "expired");

    private final CreatorPaymentRepository paymentRepository;
    private final TontineRepository tontineRepository;
    private final TontineMemberRepository memberRepository;
    private final NabooPayClient nabooPay;
    private final NotificationService notificationService;

    @Value("${billing.tontine-price:2500}")
    private BigDecimal price;

    // Adresse publique de l'app (PWA) : NabooPay y renvoie l'utilisateur apres paiement.
    @Value("${app.public-url:http://localhost:8081}")
    private String publicUrl;

    public Quote quote(User user, Purpose purpose, Long tontineId) {
        boolean free = purpose == Purpose.CREATE && isFirstTontine(user);
        boolean hasCredit = paymentRepository.findTop50ByUserIdOrderByIdDesc(user.getId()).stream()
                .anyMatch(p -> isAvailable(p, purpose, tontineId));
        return new Quote(free, hasCredit, price);
    }

    @Transactional
    public CheckoutResponse checkout(User user, Purpose purpose, Long tontineId) {
        Tontine tontine = null;
        if (purpose == Purpose.RENEW) {
            tontine = requireRenewable(user, tontineId);
        }
        Quote q = quote(user, purpose, tontineId);
        if (q.free() || q.hasCredit()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun paiement nécessaire : tu peux continuer directement.");
        }
        if (!nabooPay.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le paiement en ligne n'est pas encore activé.");
        }

        CreatorPayment payment = new CreatorPayment();
        payment.setUser(user);
        payment.setPurpose(purpose);
        payment.setTontine(tontine);
        payment.setAmount(price);
        paymentRepository.save(payment);

        String product = purpose == Purpose.CREATE ? "Création d'une tontine SèneTontine"
                : "Renouvellement de la tontine " + tontine.getName();
        String[] names = splitName(user.getFullName());
        String returnUrl = publicUrl + "/paiement?ref=" + payment.getId();
        try {
            NabooPayClient.Transaction tx = nabooPay.createTransaction(product, price, names[0], names[1],
                    internationalPhone(user.getPhone()), returnUrl, returnUrl + "&echec=1");
            if (tx.orderId() == null || tx.checkoutUrl() == null) {
                throw new IllegalStateException("Reponse NabooPay incomplete");
            }
            payment.setOrderId(tx.orderId());
            payment.setCheckoutUrl(tx.checkoutUrl());
            paymentRepository.save(payment);
            return new CheckoutResponse(payment.getId(), tx.checkoutUrl());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
    }

    // Appele par l'app au retour de la page de paiement : on revérifie aupres de NabooPay.
    @Transactional
    public PaymentStatusInfo refresh(User user, Long paymentId) {
        CreatorPayment p = paymentRepository.findById(paymentId)
                .filter(x -> x.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paiement introuvable"));
        if (p.getStatus() == Status.PENDING && p.getOrderId() != null) {
            verifyWithProvider(p.getOrderId());
            p = paymentRepository.findById(paymentId).orElseThrow();
        }
        return toInfo(p);
    }

    // Webhook NabooPay : signature verifiee, puis verification aupres de NabooPay.
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (!nabooPay.isValidSignature(rawBody, signature)) {
            log.warn("Webhook NabooPay rejete : signature invalide");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signature invalide");
        }
        String orderId;
        try {
            orderId = nabooPay.extractOrderId(rawBody);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook illisible");
        }
        if (orderId != null) verifyWithProvider(orderId);
    }

    // Filet de securite : si un webhook a ete perdu (serveur endormi...), on reverifie
    // regulierement les paiements en attente recents.
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 60 * 1000L)
    @Transactional
    public void reconcilePending() {
        if (!nabooPay.isConfigured()) return;
        Instant now = Instant.now();
        paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == Status.PENDING && p.getOrderId() != null)
                .filter(p -> p.getCreatedAt().isAfter(now.minus(Duration.ofDays(2))))
                .forEach(p -> {
                    try {
                        verifyWithProvider(p.getOrderId());
                    } catch (Exception e) {
                        log.warn("Reconciliation impossible pour la commande {} : {}", p.getOrderId(), e.getMessage());
                    }
                });
    }

    void verifyWithProvider(String orderId) {
        CreatorPayment p = paymentRepository.lockByOrderId(orderId).orElse(null);
        if (p == null || p.getStatus() != Status.PENDING) return; // inconnu ou deja traite (idempotence)

        NabooPayClient.Transaction tx;
        try {
            tx = nabooPay.getTransaction(orderId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable(e);
        } catch (Exception e) {
            throw unavailable(e);
        }
        String status = tx.status() == null ? "" : tx.status().toLowerCase();
        if (PAID_STATUSES.contains(status)) {
            if (tx.amount() == null || tx.amount().compareTo(p.getAmount()) < 0) {
                log.error("Montant NabooPay incoherent pour {} : {} au lieu de {}", orderId, tx.amount(), p.getAmount());
                return;
            }
            p.setStatus(Status.PAID);
            p.setPaidAt(Instant.now());
            paymentRepository.save(p);
            notificationService.notify(p.getUser(), NotificationType.CREATOR_PAYMENT_CONFIRMED, "Paiement reçu ✅",
                    p.getPurpose() == Purpose.CREATE
                            ? "Merci ! Tu peux maintenant créer ta nouvelle tontine."
                            : "Merci ! Tu peux maintenant renouveler ta tontine.");
        } else if (FAILED_STATUSES.contains(status)) {
            p.setStatus(Status.CANCELLED);
            paymentRepository.save(p);
        }
    }

    // A appeler dans la meme transaction que la creation de la tontine.
    public void consumeCreationRight(User user) {
        if (isFirstTontine(user)) return;
        consume(user, Purpose.CREATE, null, "Ta première tontine était offerte. Pour en créer une nouvelle, règle "
                + price.toBigInteger() + " FCFA.");
    }

    public void consumeRenewalRight(User user, Long tontineId) {
        consume(user, Purpose.RENEW, tontineId, "Le renouvellement d'une tontine coûte " + price.toBigInteger() + " FCFA.");
    }

    public Tontine requireRenewable(User user, Long tontineId) {
        Tontine t = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tontine introuvable"));
        boolean isCreator = memberRepository.findByTontineIdAndUserId(tontineId, user.getId())
                .map(m -> m.getRole() == MemberRole.CREATOR).orElse(false);
        if (!isCreator) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Seul le créateur peut renouveler cette tontine");
        if (t.getStatus() != TontineStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Seule une tontine terminée peut être renouvelée.");
        }
        if (tontineRepository.existsByRenewedFromId(tontineId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tontine a déjà été renouvelée.");
        }
        return t;
    }

    private void consume(User user, Purpose purpose, Long tontineId, String message) {
        List<CreatorPayment> rights = purpose == Purpose.CREATE
                ? paymentRepository.lockAvailableCreationRights(user.getId())
                : paymentRepository.lockAvailableRenewalRights(user.getId(), tontineId);
        if (rights.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, message);
        }
        CreatorPayment right = rights.get(0);
        right.setConsumedAt(Instant.now());
        paymentRepository.save(right);
    }

    private boolean isFirstTontine(User user) {
        return tontineRepository.countByCreatedById(user.getId()) == 0;
    }

    private static boolean isAvailable(CreatorPayment p, Purpose purpose, Long tontineId) {
        return p.getPurpose() == purpose && p.getStatus() == Status.PAID && p.getConsumedAt() == null
                && (tontineId == null || (p.getTontine() != null && p.getTontine().getId().equals(tontineId)));
    }

    private static String[] splitName(String fullName) {
        String n = fullName == null ? "" : fullName.trim();
        int i = n.indexOf(' ');
        return i < 0 ? new String[]{n.isEmpty() ? "Client" : n, n.isEmpty() ? "SeneTontine" : n}
                : new String[]{n.substring(0, i), n.substring(i + 1).trim()};
    }

    static String internationalPhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.startsWith("00221")) digits = digits.substring(2);
        if (digits.length() == 9) digits = "221" + digits;
        return "+" + digits;
    }

    private ResponseStatusException unavailable(Exception e) {
        log.error("Erreur NabooPay", e);
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Le service de paiement est momentanément indisponible, réessaie.");
    }

    private PaymentStatusInfo toInfo(CreatorPayment p) {
        return new PaymentStatusInfo(p.getId(), p.getPurpose().name(),
                p.getTontine() != null ? p.getTontine().getId() : null, p.getStatus().name(), p.getAmount());
    }
}
