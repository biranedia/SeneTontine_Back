package com.senetontine.service;

import com.senetontine.dto.IncidentDtos.IncidentInfo;
import com.senetontine.dto.IncidentDtos.SubmitIncidentRequest;
import com.senetontine.model.AuditAction;
import com.senetontine.model.IncidentReport;
import com.senetontine.model.IncidentStatus;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.repository.IncidentReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

// Signalement d'incident cote membre (fraude suspectee, litige de paiement, comportement
// abusif, bug technique) -> file d'attente traitee par un admin plateforme. Volontairement
// ouvert a TOUT utilisateur authentifie, meme non verifie KYC : un probleme sur son propre
// compte (ex: KYC bloque a tort) doit pouvoir etre signale AVANT d'etre verifie.
@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentReportRepository incidentRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public IncidentInfo submit(User reporter, SubmitIncidentRequest req) {
        IncidentReport report = new IncidentReport();
        report.setReporter(reporter);
        report.setCategory(req.category());
        report.setTargetType(req.targetType());
        report.setTargetId(req.targetId());
        report.setDescription(req.description().trim());
        return toInfo(incidentRepository.save(report));
    }

    // Signalements du membre appelant uniquement - jamais ceux d'un autre (pas d'id passe en
    // parametre, scope automatique via le token d'authentification, meme pattern que
    // StatsService/UserController.me).
    public List<IncidentInfo> mine(User reporter) {
        return incidentRepository.findByReporterIdOrderByIdDesc(reporter.getId()).stream().map(this::toInfo).toList();
    }

    // statusFilter facultatif : null = tout, sinon uniquement ce statut (ex: la file "OPEN"
    // par defaut cote admin, pour ne pas noyer les signalements actifs sous l'historique deja
    // traite).
    public List<IncidentInfo> listAll(User caller, IncidentStatus statusFilter) {
        requireAdmin(caller);
        List<IncidentReport> reports = statusFilter == null
                ? incidentRepository.findTop200ByOrderByIdDesc()
                : incidentRepository.findTop200ByStatusOrderByIdDesc(statusFilter);
        return reports.stream().map(this::toInfo).toList();
    }

    @Transactional
    public IncidentInfo resolve(User admin, Long incidentId, IncidentStatus newStatus, String adminNote) {
        requireAdmin(admin);
        if (newStatus == IncidentStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Statut invalide");
        }

        IncidentReport report = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Signalement introuvable"));

        IncidentStatus previousStatus = report.getStatus();
        report.setStatus(newStatus);
        report.setAdminNote(adminNote);
        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.DISMISSED) {
            report.setResolvedBy(admin);
            report.setResolvedAt(Instant.now());
        }
        incidentRepository.save(report);

        auditLogService.log(admin, AuditAction.INCIDENT_STATUS_CHANGED, "IncidentReport", report.getId(),
                previousStatus + " → " + newStatus + (adminNote != null && !adminNote.isBlank() ? " (" + adminNote + ")" : ""));

        return toInfo(report);
    }

    private IncidentInfo toInfo(IncidentReport r) {
        return new IncidentInfo(
                r.getId(), r.getReporter().getFullName(), r.getCategory().name(),
                r.getTargetType(), r.getTargetId(), r.getDescription(), r.getStatus().name(),
                r.getAdminNote(), r.getResolvedBy() != null ? r.getResolvedBy().getFullName() : null,
                r.getCreatedAt().toString(), r.getResolvedAt() != null ? r.getResolvedAt().toString() : null
        );
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
    }
}
