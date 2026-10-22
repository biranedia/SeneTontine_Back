package com.senetontine.service;

import com.senetontine.dto.TontineDtos.CreateTontineRequest;
import com.senetontine.dto.TontineDtos.LeaveTontineResponse;
import com.senetontine.dto.TontineDtos.MemberInfo;
import com.senetontine.dto.TontineDtos.TontineDetail;
import com.senetontine.dto.TontineDtos.TontineSummary;
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
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TontineService {

    private final TontineRepository tontineRepository;
    private final TontineMemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final BillingService billingService;
    private final PotPayoutRepository potPayoutRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH);
    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O/1/I : ambigus a l'oral/a l'ecrit
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public TontineSummary create(User creator, CreateTontineRequest req) {
        // 1re tontine offerte, ensuite un droit paye via NabooPay (voir BillingService).
        billingService.consumeCreationRight(creator);
        Tontine tontine = new Tontine();
        applySettings(tontine, req);
        tontine.setCreatedBy(creator);
        tontine.setStatus(TontineStatus.PENDING);
        tontine.setInviteCode(generateUniqueInviteCode());
        tontineRepository.save(tontine);

        TontineMember member = new TontineMember();
        member.setTontine(tontine);
        member.setUser(creator);
        member.setRole(MemberRole.CREATOR);
        member.setTurnOrder(1);
        memberRepository.save(member);

        return toSummary(tontine, creator.getId());
    }

    // Renouvellement d'une tontine terminee : memes parametres, memes membres (reinvites et
    // prevenus ; ils peuvent quitter avant le demarrage). Paye via NabooPay (BillingService).
    @Transactional
    public TontineSummary renew(Long tontineId, User caller) {
        Tontine old = billingService.requireRenewable(caller, tontineId);
        billingService.consumeRenewalRight(caller, tontineId);

        Tontine t = new Tontine();
        t.setName(old.getName());
        t.setEmoji(old.getEmoji());
        t.setCotisationAmount(old.getCotisationAmount());
        t.setMembersRequired(old.getMembersRequired());
        t.setRotationType(old.getRotationType());
        t.setPenaltyPerDay(old.getPenaltyPerDay());
        t.setCycleDurationDays(old.getCycleDurationDays());
        t.setVisibility(TontineVisibility.PRIVATE);
        t.setWavePhone(old.getWavePhone());
        t.setOrangeMoneyPhone(old.getOrangeMoneyPhone());
        t.setCreatedBy(caller);
        t.setStatus(TontineStatus.PENDING);
        t.setInviteCode(generateUniqueInviteCode());
        t.setRenewedFrom(old);
        tontineRepository.save(t);

        List<TontineMember> previous = memberRepository.findByTontineId(tontineId).stream()
                .filter(m -> m.getUser().isActive())
                .sorted((a, b) -> Integer.compare(
                        a.getTurnOrder() == null ? Integer.MAX_VALUE : a.getTurnOrder(),
                        b.getTurnOrder() == null ? Integer.MAX_VALUE : b.getTurnOrder()))
                .toList();
        int order = 1;
        for (TontineMember m : previous) {
            TontineMember copy = new TontineMember();
            copy.setTontine(t);
            copy.setUser(m.getUser());
            copy.setRole(m.getRole());
            copy.setTurnOrder(order++);
            memberRepository.save(copy);
            if (m.getRole() != MemberRole.CREATOR) {
                notificationService.notify(m.getUser(), NotificationType.TONTINE_RENEWED, "La tontine repart 🔁",
                        "\"" + t.getName() + "\" est renouvelée et tu en fais à nouveau partie. Si tu ne veux pas continuer, "
                                + "quitte-la avant son démarrage.");
            }
        }
        if (previous.size() >= t.getMembersRequired()) {
            start(t);
            tontineRepository.save(t);
        }
        return toSummary(t, caller.getId());
    }

    // Parametres modifiables uniquement tant que la tontine est en recrutement (PENDING) : une
    // fois ACTIVE, des membres ont deja cotise sur la base de ces conditions.
    @Transactional
    public TontineDetail updateSettings(Long tontineId, User caller, CreateTontineRequest req) {
        Tontine tontine = findTontine(tontineId);
        requireCreatorOrPlatformAdmin(tontine, caller, "Seul le créateur de la tontine peut modifier ses paramètres");
        if (tontine.getStatus() != TontineStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette tontine a déjà démarré : ses paramètres ne peuvent plus être modifiés.");
        }
        long currentCount = memberRepository.countByTontineId(tontineId);
        if (req.membersRequired() < currentCount) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cette tontine compte déjà " + currentCount + " membre(s) : impossible de fixer un nombre requis inférieur.");
        }
        applySettings(tontine, req);
        if (currentCount >= tontine.getMembersRequired()) {
            start(tontine);
        }
        tontineRepository.save(tontine);
        return getDetail(tontineId, caller);
    }

    private void applySettings(Tontine tontine, CreateTontineRequest req) {
        String wave = blankToNull(req.wavePhone());
        String om = blankToNull(req.orangeMoneyPhone());
        if (wave == null && om == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique au moins un numéro Wave ou Orange Money pour recevoir les cotisations.");
        }
        tontine.setName(req.name().trim());
        tontine.setCotisationAmount(req.cotisationAmount());
        tontine.setMembersRequired(req.membersRequired());
        if (req.rotationType() != null) tontine.setRotationType(req.rotationType());
        tontine.setPenaltyPerDay(req.penaltyPerDay() != null ? req.penaltyPerDay() : BigDecimal.ZERO);
        if (req.cycleDurationDays() != null) tontine.setCycleDurationDays(req.cycleDurationDays());
        // Plus de tontines publiques : un escroc pourrait en publier une et piéger des inconnus.
        // On ne rejoint une tontine que sur invitation (code ou ajout par numéro).
        tontine.setVisibility(TontineVisibility.PRIVATE);
        tontine.setWavePhone(wave);
        tontine.setOrangeMoneyPhone(om);
        LocalDate start = req.startDate();
        if (start != null && (start.isBefore(LocalDate.now()) || start.isAfter(LocalDate.now().plusYears(1)))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La date du premier tour doit être aujourd'hui ou dans les 12 prochains mois.");
        }
        tontine.setStartDate(start);
    }

    // Une tontine PRIVATE ne se rejoint que via code d'invitation ou ajout direct par son
    // createur ; seules les PUBLIC sont rejoignables par leur ID (correctif IDOR historique).
    @Transactional
    public TontineSummary join(Long tontineId, User user) {
        Tontine tontine = findTontine(tontineId);
        if (tontine.getVisibility() != TontineVisibility.PUBLIC) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette tontine est privée : demande un code d'invitation à son créateur pour la rejoindre");
        }
        return addMember(tontine, user);
    }

    public List<TontineSummary> discover(User user) {
        if (true) return List.of(); // découverte publique désactivée (sécurité anti-arnaque)
        return tontineRepository.findTop50ByVisibilityAndStatusOrderByIdDesc(TontineVisibility.PUBLIC, TontineStatus.PENDING)
                .stream()
                .filter(t -> memberRepository.findByTontineIdAndUserId(t.getId(), user.getId()).isEmpty())
                .map(t -> toSummary(t, user.getId()))
                .toList();
    }

    @Transactional
    public TontineSummary joinByCode(String inviteCode, User user) {
        Tontine tontine = tontineRepository.findByInviteCode(inviteCode.trim().toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Code d'invitation invalide"));
        return addMember(tontine, user);
    }

    @Transactional
    public TontineSummary addMemberByPhone(Long tontineId, String phone, User caller) {
        Tontine tontine = findTontine(tontineId);
        requireCreatorOrPlatformAdmin(tontine, caller, "Seul le créateur de la tontine peut y ajouter des membres");
        User target = userRepository.findByPhone(phone.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Aucun compte SèneTontine n'est associé à ce numéro. La personne doit d'abord créer un compte."));
        return addMember(tontine, target);
    }

    private TontineSummary addMember(Tontine tontine, User user) {
        if (tontine.getStatus() == TontineStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tontine a été annulée : elle ne peut plus être rejointe.");
        }
        if (tontine.getStatus() != TontineStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tontine a déjà démarré : elle ne peut plus être rejointe.");
        }
        if (memberRepository.findByTontineIdAndUserId(tontine.getId(), user.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette personne est déjà membre de cette tontine");
        }
        long currentCount = memberRepository.countByTontineId(tontine.getId());
        if (currentCount >= tontine.getMembersRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tontine est déjà complète");
        }

        TontineMember member = new TontineMember();
        member.setTontine(tontine);
        member.setUser(user);
        member.setRole(MemberRole.MEMBER);
        member.setTurnOrder((int) currentCount + 1);
        memberRepository.save(member);

        notificationService.notify(user, NotificationType.ADDED_TO_TONTINE,
                "Nouvelle tontine 🤝", "Tu as rejoint la tontine \"" + tontine.getName() + "\".");

        if (currentCount + 1 >= tontine.getMembersRequired()) {
            start(tontine);
            tontineRepository.save(tontine);
            memberRepository.findByTontineId(tontine.getId()).forEach(m ->
                    notificationService.notify(m.getUser(), NotificationType.ROUND_COMPLETED,
                            "La tontine démarre 🚀",
                            "\"" + tontine.getName() + "\" est complète. Première cotisation à envoyer avant le "
                                    + tontine.getNextTurnDate().format(DATE_FMT) + "."));
        }
        return toSummary(tontine, user.getId());
    }

    private void start(Tontine tontine) {
        tontine.setStatus(TontineStatus.ACTIVE);
        tontine.setCurrentRound(1);
        LocalDate first = tontine.getStartDate();
        tontine.setNextTurnDate(first != null && first.isAfter(LocalDate.now())
                ? first : LocalDate.now().plusDays(tontine.getCycleDurationDays()));
    }

    // Quitter une tontine. Interdit au createur, et interdit a un membre qui a deja recu son
    // propre tour (il partirait avec l'argent du groupe sans finir de cotiser). Comme l'app ne
    // detient aucun argent, une cotisation deja envoyee pour le tour en cours se regle
    // directement avec le createur (signale dans la reponse et la notification).
    @Transactional
    public LeaveTontineResponse leave(Long tontineId, User user) {
        Tontine tontine = findTontine(tontineId);
        TontineMember membership = memberRepository.findByTontineIdAndUserId(tontineId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu n'es pas membre de cette tontine"));

        if (membership.getRole() == MemberRole.CREATOR) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le créateur ne peut pas quitter sa propre tontine. Un administrateur peut l'annuler si besoin.");
        }
        if (tontine.getStatus() == TontineStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette tontine est terminée.");
        }
        if (tontine.getStatus() == TontineStatus.ACTIVE
                && membership.getTurnOrder() != null
                && membership.getTurnOrder() < tontine.getCurrentRound()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Tu as déjà reçu ton tour de versement : impossible de quitter avant la fin de la tontine.");
        }

        boolean settleWithCreator = false;
        BigDecimal amountPaid = BigDecimal.ZERO;
        if (tontine.getStatus() == TontineStatus.ACTIVE) {
            Optional<Payment> current = paymentRepository
                    .findByTontineIdAndUserIdAndRoundNumber(tontineId, user.getId(), tontine.getCurrentRound());
            if (current.isPresent()) {
                Payment p = current.get();
                if (p.getStatus() != PaymentStatus.DUE) {
                    settleWithCreator = true;
                    amountPaid = p.getAmount().add(p.getPenaltyAmount());
                }
                paymentRepository.delete(p);
            }
        }

        Integer leavingTurnOrder = membership.getTurnOrder();
        memberRepository.delete(membership);

        List<TontineMember> remaining = memberRepository.findByTontineId(tontineId);
        if (leavingTurnOrder != null) {
            for (TontineMember m : remaining) {
                if (m.getTurnOrder() != null && m.getTurnOrder() > leavingTurnOrder) {
                    m.setTurnOrder(m.getTurnOrder() - 1);
                    memberRepository.save(m);
                }
            }
        }
        // En recrutement : la place se rouvre simplement (taille du groupe inchangee).
        // En cours : un tour par membre restant ; a moins de 2, la tontine ne peut plus tourner.
        if (tontine.getStatus() == TontineStatus.ACTIVE) {
            tontine.setMembersRequired(remaining.size());
            if (remaining.size() < 2) {
                tontine.setStatus(TontineStatus.CANCELLED);
            } else if (tontine.getCurrentRound() > remaining.size()) {
                tontine.setStatus(TontineStatus.COMPLETED);
            }
        }
        tontineRepository.save(tontine);
        String outcome = tontine.getStatus() == TontineStatus.CANCELLED
                ? " Il ne reste pas assez de membres : la tontine est annulée."
                : tontine.getStatus() == TontineStatus.COMPLETED ? " Tous les membres restants ont eu leur tour : la tontine est terminée." : "";

        String extra = settleWithCreator
                ? " Sa cotisation du tour en cours (" + formatFcfa(amountPaid) + ") est à lui rembourser directement."
                : "";
        memberRepository.findByTontineId(tontineId).forEach(m ->
                notificationService.notify(m.getUser(), NotificationType.MEMBER_LEFT,
                        "Un membre a quitté la tontine",
                        user.getFullName() + " a quitté \"" + tontine.getName() + "\"." + outcome
                                + (m.getRole() == MemberRole.CREATOR ? extra : "")));

        return new LeaveTontineResponse(settleWithCreator, amountPaid);
    }

    public List<TontineSummary> listForUser(Long userId) {
        return tontineRepository.findAllForUser(userId).stream().map(t -> toSummary(t, userId)).toList();
    }

    public TontineDetail getDetail(Long tontineId, User caller) {
        Tontine t = findTontine(tontineId);
        // IDOR : seuls les membres et les admins plateforme voient le detail (dont le code
        // d'invitation et les numeros Wave/OM du createur).
        if (caller.getRole() != UserRole.ADMIN
                && memberRepository.findByTontineIdAndUserId(tontineId, caller.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tu n'es pas membre de cette tontine");
        }

        List<TontineMember> members = memberRepository.findByTontineId(tontineId).stream()
                .sorted((a, b) -> Integer.compare(
                        a.getTurnOrder() == null ? Integer.MAX_VALUE : a.getTurnOrder(),
                        b.getTurnOrder() == null ? Integer.MAX_VALUE : b.getTurnOrder()))
                .toList();

        List<MemberInfo> memberInfos = members.stream().map(m -> {
            Optional<Payment> p = currentPayment(t, m.getUser().getId());
            BigDecimal due = p.map(x -> x.getAmount().add(x.getPenaltyAmount())).orElse(t.getCotisationAmount());
            return new MemberInfo(
                    m.getUser().getId(),
                    m.getUser().getFullName(),
                    initials(m.getUser().getFullName()),
                    (m.getRole() == MemberRole.CREATOR ? "Créateur" : "Membre") + " · Tour " + m.getTurnOrder(),
                    p.map(x -> statusKey(x.getStatus())).orElse("due"),
                    m.getRole() == MemberRole.CREATOR,
                    m.getTurnOrder(),
                    p.map(Payment::getId).orElse(null),
                    p.map(Payment::getReference).orElse(null),
                    p.map(x -> x.getMethod() != null ? x.getMethod().name() : null).orElse(null),
                    due,
                    m.getUser().getAvatarBase64()
            );
        }).toList();

        BigDecimal pot = t.getCotisationAmount().multiply(BigDecimal.valueOf(t.getMembersRequired()));

        return new TontineDetail(
                t.getId(), t.getName(), t.getEmoji(), members.size(),
                tourLabel(t, members.size()),
                formatFcfa(t.getCotisationAmount()), t.getCotisationAmount(),
                t.getNextTurnDate() != null ? t.getNextTurnDate().format(DATE_FMT) : "À définir",
                beneficiaireLabel(t, members),
                t.getStatus().name(),
                progress(t, members.size()),
                t.getCreatedBy().getFullName(),
                formatFcfa(pot),
                memberInfos,
                t.getInviteCode(),
                t.getMembersRequired(),
                t.getCurrentRound(),
                t.getCycleDurationDays(),
                t.getRotationType().name(),
                t.getPenaltyPerDay(),
                t.getVisibility() == TontineVisibility.PUBLIC,
                t.getWavePhone(),
                t.getOrangeMoneyPhone(),
                t.getStartDate() != null ? t.getStartDate().toString() : null
        );
    }

    private Tontine findTontine(Long id) {
        return tontineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tontine introuvable"));
    }

    private void requireCreatorOrPlatformAdmin(Tontine tontine, User caller, String message) {
        if (caller.getRole() == UserRole.ADMIN) return;
        boolean isCreator = memberRepository.findByTontineIdAndUserId(tontine.getId(), caller.getId())
                .map(m -> m.getRole() == MemberRole.CREATOR)
                .orElse(false);
        if (!isCreator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
    }

    private String generateUniqueInviteCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(8);
            for (int i = 0; i < 8; i++) {
                sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
            }
            code = sb.toString();
        } while (tontineRepository.existsByInviteCode(code));
        return code;
    }

    private TontineSummary toSummary(Tontine t, Long viewerId) {
        long memberCount = memberRepository.countByTontineId(t.getId());
        String myStatus = "none";
        if (t.getStatus() == TontineStatus.ACTIVE) {
            myStatus = currentPayment(t, viewerId).map(p -> statusKey(p.getStatus())).orElse("due");
        }
        boolean iAmCreator = memberRepository.findByTontineIdAndUserId(t.getId(), viewerId)
                .map(m -> m.getRole() == MemberRole.CREATOR).orElse(false);
        int pending = iAmCreator && t.getStatus() == TontineStatus.ACTIVE
                ? (int) paymentRepository.countByTontineIdAndRoundNumberAndStatus(t.getId(), t.getCurrentRound(), PaymentStatus.DECLARED)
                : 0;
        PotPayout openPot = potPayoutRepository.findByTontineIdOrderByRoundNumberAsc(t.getId()).stream()
                .filter(p -> p.getReceivedAt() == null)
                .findFirst().orElse(null);
        String potAction = null;
        if (openPot != null) {
            if (iAmCreator && openPot.getSentAt() == null) potAction = "SEND";
            else if (openPot.getBeneficiary().getId().equals(viewerId)) potAction = "RECEIVE";
        }
        return new TontineSummary(
                t.getId(), t.getName(), t.getEmoji(), (int) memberCount,
                tourLabel(t, (int) memberCount),
                formatFcfa(t.getCotisationAmount()), t.getCotisationAmount(),
                t.getNextTurnDate() != null ? t.getNextTurnDate().format(DATE_FMT) : "À définir",
                "—",
                t.getStatus().name(),
                progress(t, (int) memberCount),
                myStatus,
                iAmCreator,
                pending,
                potAction,
                potAction != null ? openPot.getAmount() : null,
                potAction != null ? openPot.getBeneficiary().getFullName() : null
        );
    }

    private Optional<Payment> currentPayment(Tontine t, Long userId) {
        return paymentRepository.findByTontineIdAndUserIdAndRoundNumber(t.getId(), userId, t.getCurrentRound());
    }

    private static String statusKey(PaymentStatus s) {
        return switch (s) {
            case PAID -> "paid";
            case LATE -> "late";
            case DECLARED -> "declared";
            case DUE -> "due";
        };
    }

    private String tourLabel(Tontine t, int memberCount) {
        return switch (t.getStatus()) {
            case CANCELLED -> "Tontine annulée";
            case COMPLETED -> "Tontine terminée";
            case ACTIVE -> "Tour " + t.getCurrentRound() + "/" + t.getMembersRequired();
            case PENDING -> memberCount + "/" + t.getMembersRequired() + " membres";
        };
    }

    private double progress(Tontine t, int memberCount) {
        if (t.getMembersRequired() == null || t.getMembersRequired() == 0) return 0;
        if (t.getStatus() == TontineStatus.COMPLETED) return 1;
        if (t.getStatus() == TontineStatus.ACTIVE) {
            return (t.getCurrentRound() - 1) / (double) t.getMembersRequired();
        }
        return memberCount / (double) t.getMembersRequired();
    }

    private String beneficiaireLabel(Tontine t, List<TontineMember> members) {
        if (t.getStatus() != TontineStatus.ACTIVE) return "—";
        return members.stream()
                .filter(m -> t.getCurrentRound().equals(m.getTurnOrder()))
                .findFirst()
                .map(m -> m.getUser().getFullName())
                .orElse("À déterminer");
    }

    private String initials(String fullName) {
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty()) sb.append(Character.toUpperCase(p.charAt(0)));
            if (sb.length() >= 2) break;
        }
        return sb.toString();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String formatFcfa(BigDecimal amount) {
        return String.format(Locale.US, "%,d", amount.longValue()).replace(",", " ") + " FCFA";
    }
}
