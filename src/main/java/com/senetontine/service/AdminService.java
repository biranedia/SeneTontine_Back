package com.senetontine.service;

import com.senetontine.dto.AdminDtos.AdminTontineInfo;
import com.senetontine.dto.AdminDtos.AdminUserInfo;
import com.senetontine.dto.AdminDtos.PaymentHistoryItem;
import com.senetontine.dto.AdminDtos.PlatformOverview;
import com.senetontine.dto.AdminDtos.TontineMembershipItem;
import com.senetontine.dto.AdminDtos.UserDetailInfo;
import com.senetontine.model.AuditAction;
import com.senetontine.model.PaymentStatus;
import com.senetontine.model.Tontine;
import com.senetontine.model.TontineStatus;
import com.senetontine.model.IncidentStatus;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.repository.IncidentReportRepository;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import com.senetontine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Tableau de bord + gestion de plateforme, reserve aux comptes ADMIN. A la difference de
// d'autres services (autorisation data-dependante, ex: createur d'UNE tontine precise),
// il n'y a ici aucune notion de "superviseur partiel" : chaque methode agit sur TOUTE la
// plateforme (n'importe quel utilisateur, n'importe quelle tontine), donc seul un admin
// plateforme peut y avoir acces - jamais de logique alternative pour un role intermediaire.
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final TontineRepository tontineRepository;
    private final TontineMemberRepository tontineMemberRepository;
    private final PaymentRepository paymentRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final AuditLogService auditLogService;

    public PlatformOverview getOverview(User caller) {
        requireAdmin(caller);

        long totalUsers = userRepository.count();

        long totalTontines = tontineRepository.count();
        long activeTontines = tontineRepository.countByStatus(TontineStatus.ACTIVE);
        long pendingTontines = tontineRepository.countByStatus(TontineStatus.PENDING);

        long totalPaymentsMade = paymentRepository.countByStatus(PaymentStatus.PAID);
        long pendingDeclarations = paymentRepository.countByStatus(PaymentStatus.DECLARED);
        long openIncidents = incidentReportRepository.countByStatus(IncidentStatus.OPEN);

        return new PlatformOverview(
                totalUsers,
                totalTontines, activeTontines, pendingTontines,
                paymentRepository.sumAllPaidAmounts(),
                pendingDeclarations, totalPaymentsMade, openIncidents
        );
    }

    // query facultatif : recherche sur nom OU telephone (voir UserRepository). null/vide =
    // liste brute des 100 derniers inscrits.
    public List<AdminUserInfo> listUsers(User caller, String query) {
        requireAdmin(caller);

        List<User> users = (query == null || query.isBlank())
                ? userRepository.findTop100ByOrderByIdDesc()
                : userRepository.findTop100ByFullNameContainingIgnoreCaseOrPhoneContainingIgnoreCaseOrderByIdDesc(query.trim(), query.trim());

        return users.stream().map(this::toUserInfo).toList();
    }

    // Fiche complete d'un utilisateur : ses tontines, son historique de paiements effectues, et
    // le nombre de signalements qu'il a lui-meme soumis - tout ce qu'un admin a besoin de voir
    // en un coup d'oeil pour instruire un litige, sans naviguer entre plusieurs ecrans.
    public UserDetailInfo getUserDetail(User caller, Long targetUserId) {
        requireAdmin(caller);
        User target = findUserOrThrow(targetUserId);

        List<TontineMembershipItem> tontines = tontineMemberRepository.findByUserId(targetUserId).stream()
                .map(tm -> new TontineMembershipItem(
                        tm.getTontine().getId(), tm.getTontine().getName(), tm.getTontine().getEmoji(),
                        tm.getRole().name()))
                .toList();

        List<PaymentHistoryItem> payments = paymentRepository.findByUserIdAndStatus(targetUserId, PaymentStatus.PAID).stream()
                .map(p -> new PaymentHistoryItem(
                        p.getTontine().getId(), p.getTontine().getName(), p.getRoundNumber(),
                        p.getAmount(), p.getPenaltyAmount(), p.getStatus().name(),
                        p.getPaidAt() == null ? null : p.getPaidAt().toString()))
                .toList();

        long incidentsCount = incidentReportRepository.findByReporterIdOrderByIdDesc(targetUserId).size();

        return new UserDetailInfo(toUserInfo(target), tontines, payments, incidentsCount);
    }

    @Transactional
    public AdminUserInfo setActive(User caller, Long targetUserId, boolean active) {
        requireAdmin(caller);
        User target = findUserOrThrow(targetUserId);

        // Un admin ne peut pas se bloquer lui-meme : sinon un admin distrait (ou malveillant en
        // train de couvrir ses traces) pourrait se retrouver - ou se mettre volontairement -
        // hors-jeu sans que personne d'autre ne puisse le debloquer si c'etait le seul admin.
        if (target.getId().equals(caller.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de modifier ton propre compte de cette façon");
        }
        // Un admin ne peut pas non plus bloquer un AUTRE admin directement : il faut d'abord le
        // retrograder en MEMBER (action separee, elle-meme tracee). Evite qu'un admin bloque
        // silencieusement un pair pour l'evincer sans que ce soit visible comme un vrai
        // changement de role dans le journal d'audit.
        if (target.getRole() == UserRole.ADMIN && !active) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de bloquer un autre administrateur - rétrograde-le d'abord en membre");
        }

        target.setActive(active);
        userRepository.save(target);
        auditLogService.log(caller, active ? AuditAction.USER_UNBLOCKED : AuditAction.USER_BLOCKED,
                "User", target.getId(), target.getFullName() + " (" + target.getPhone() + ")");

        return toUserInfo(target);
    }

    @Transactional
    public AdminUserInfo setRole(User caller, Long targetUserId, UserRole newRole) {
        requireAdmin(caller);
        User target = findUserOrThrow(targetUserId);

        if (target.getId().equals(caller.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de modifier ton propre rôle");
        }
        // Dernier rempart : si on retrograde le DERNIER admin restant, plus personne ne pourrait
        // jamais promouvoir qui que ce soit d'autre - la plateforme se retrouverait bloquee sans
        // aucun administrateur, de facon irreversible sans intervention directe en base.
        if (target.getRole() == UserRole.ADMIN && newRole != UserRole.ADMIN && userRepository.countByRole(UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de rétrograder le dernier administrateur de la plateforme");
        }

        UserRole previousRole = target.getRole();
        target.setRole(newRole);
        userRepository.save(target);
        auditLogService.log(caller, AuditAction.USER_ROLE_CHANGED, "User", target.getId(),
                target.getFullName() + " : " + previousRole + " → " + newRole);

        return toUserInfo(target);
    }

    // Export CSV brut des 100 derniers inscrits - meme perimetre que listUsers() sans recherche,
    // pour que l'export corresponde exactement a ce que l'admin voit dans la liste par defaut.
    public String exportUsersCsv(User caller) {
        requireAdmin(caller);
        StringBuilder sb = new StringBuilder("id,nom,telephone,role,actif,inscription\n");
        for (User u : userRepository.findTop100ByOrderByIdDesc()) {
            sb.append(u.getId()).append(',')
                    .append(csvEscape(u.getFullName())).append(',')
                    .append(csvEscape(u.getPhone())).append(',')
                    .append(u.getRole()).append(',')
                    .append(u.isActive()).append(',')
                    .append(u.getCreatedAt()).append('\n');
        }
        return sb.toString();
    }

    public String exportTontinesCsv(User caller) {
        requireAdmin(caller);
        List<Tontine> tontines = tontineRepository.findTop200ByOrderByIdDesc();
        Map<Long, Integer> memberCounts = countMembersGrouped(tontines);
        StringBuilder sb = new StringBuilder("id,nom,statut,membres,membres_requis,cotisation,createur,creation\n");
        for (Tontine t : tontines) {
            int membersCount = memberCounts.getOrDefault(t.getId(), 0);
            sb.append(t.getId()).append(',')
                    .append(csvEscape(t.getName())).append(',')
                    .append(t.getStatus()).append(',')
                    .append(membersCount).append(',')
                    .append(t.getMembersRequired()).append(',')
                    .append(t.getCotisationAmount()).append(',')
                    .append(csvEscape(t.getCreatedBy().getFullName())).append(',')
                    .append(t.getCreatedAt()).append('\n');
        }
        return sb.toString();
    }

    // Echappement CSV minimal (RFC 4180) : entoure de guillemets et double les guillemets
    // internes des qu'une valeur contient une virgule, un guillemet ou un retour a la ligne -
    // un nom d'utilisateur ou de tontine peut tout a fait contenir une virgule.
    private String csvEscape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // Supervision : TOUTES les tontines de la plateforme (contrairement a listForUser, qui ne
    // renvoie que celles de l'appelant). Taper sur une tontine ouvre son detail normalement
    // (TontineService.getDetail laisse deja passer un admin sur n'importe quelle tontine) ; en
    // plus de ca, un admin peut forcer son statut (setTontineStatus) depuis cette liste.
    public List<AdminTontineInfo> listTontines(User caller) {
        requireAdmin(caller);
        List<Tontine> tontines = tontineRepository.findTop200ByOrderByIdDesc();
        // Comptage des membres en UNE requete groupee plutot qu'un countByTontineId() par
        // tontine dans la boucle (voir toTontineInfo plus bas) - jusqu'a 200 requetes SQL en
        // plus sur cet endpoint sinon, un vrai probleme de performance des que la plateforme
        // compte beaucoup de tontines.
        Map<Long, Integer> memberCounts = countMembersGrouped(tontines);
        return tontines.stream().map(t -> toTontineInfo(t, memberCounts.getOrDefault(t.getId(), 0))).toList();
    }

    private Map<Long, Integer> countMembersGrouped(List<Tontine> tontines) {
        List<Long> ids = tontines.stream().map(Tontine::getId).toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, Integer> counts = new HashMap<>();
        for (var row : tontineMemberRepository.countGroupedByTontineIds(ids)) {
            counts.put(row.getTontineId(), (int) row.getCnt());
        }
        return counts;
    }

    @Transactional
    public AdminTontineInfo setTontineStatus(User caller, Long tontineId, TontineStatus newStatus) {
        requireAdmin(caller);
        Tontine t = tontineRepository.findById(tontineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tontine introuvable"));

        TontineStatus previous = t.getStatus();
        t.setStatus(newStatus);
        tontineRepository.save(t);
        auditLogService.log(caller, AuditAction.TONTINE_STATUS_CHANGED, "Tontine", t.getId(),
                t.getName() + " : " + previous + " → " + newStatus);

        return toTontineInfo(t);
    }


    private AdminUserInfo toUserInfo(User u) {
        return new AdminUserInfo(
                u.getId(), u.getFullName(), u.getPhone(), u.getRole().name(),
                u.isActive(), u.getCreatedAt().toString()
        );
    }

    // Cas d'une SEULE tontine (ex: apres setTontineStatus) : pas besoin de la version groupee
    // ci-dessus, une simple requete suffit.
    private AdminTontineInfo toTontineInfo(Tontine t) {
        return toTontineInfo(t, (int) tontineMemberRepository.countByTontineId(t.getId()));
    }

    private AdminTontineInfo toTontineInfo(Tontine t, int membersCount) {
        return new AdminTontineInfo(
                t.getId(), t.getName(), t.getEmoji(), t.getStatus().name(),
                membersCount, t.getMembersRequired(), t.getCotisationAmount(),
                t.getCreatedBy().getFullName(), t.getCreatedAt().toString()
        );
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
    }
}
