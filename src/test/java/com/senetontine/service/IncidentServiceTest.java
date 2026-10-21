package com.senetontine.service;

import com.senetontine.dto.IncidentDtos.SubmitIncidentRequest;
import com.senetontine.model.*;
import com.senetontine.repository.IncidentReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

// Signalement d'incident cote membre -> file d'attente admin (voir IncidentService). Couvre
// surtout ce qui protege la plateforme d'un abus : un membre ne doit jamais voir les
// signalements d'un autre, et seul un admin peut lister/resoudre.
@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock private IncidentReportRepository incidentRepository;
    @Mock private AuditLogService auditLogService;

    private IncidentService incidentService;

    private User reporter;
    private User admin;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(incidentRepository, auditLogService);

        reporter = new User();
        reporter.setId(1L);
        reporter.setFullName("Fatou Ndiaye");
        reporter.setRole(UserRole.MEMBER);

        admin = new User();
        admin.setId(2L);
        admin.setRole(UserRole.ADMIN);
    }

    @Test
    void submit_savesReportTiedToTheReporter() {
        when(incidentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var req = new SubmitIncidentRequest(IncidentCategory.FRAUD, null, null, "Un membre me demande de payer hors de l'app");

        incidentService.submit(reporter, req);

        ArgumentCaptor<IncidentReport> captor = ArgumentCaptor.forClass(IncidentReport.class);
        verify(incidentRepository).save(captor.capture());
        assertThat(captor.getValue().getReporter()).isEqualTo(reporter);
        assertThat(captor.getValue().getStatus()).isEqualTo(IncidentStatus.OPEN);
    }

    @Test
    void mine_isScopedToTheCallingReporterOnly() {
        incidentService.mine(reporter);
        // Aucun autre moyen de filtrage n'est propose au caller : pas d'id passe en parametre,
        // c'est bien reporter.getId() (1L) qui doit etre utilise, jamais un id arbitraire.
        verify(incidentRepository).findByReporterIdOrderByIdDesc(1L);
    }

    @Test
    void listAll_rejectsNonAdmin() {
        assertThatThrownBy(() -> incidentService.listAll(reporter, null))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(incidentRepository);
    }

    @Test
    void listAll_allowsAdmin() {
        when(incidentRepository.findTop200ByOrderByIdDesc()).thenReturn(List.of());
        incidentService.listAll(admin, null);
        verify(incidentRepository).findTop200ByOrderByIdDesc();
    }

    @Test
    void resolve_rejectsSettingStatusBackToOpen() {
        assertThatThrownBy(() -> incidentService.resolve(admin, 5L, IncidentStatus.OPEN, null))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(incidentRepository);
    }

    @Test
    void resolve_rejectsNonAdmin() {
        assertThatThrownBy(() -> incidentService.resolve(reporter, 5L, IncidentStatus.RESOLVED, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void resolve_setsResolvedByAndAuditLogs() {
        IncidentReport report = new IncidentReport();
        report.setId(5L);
        report.setStatus(IncidentStatus.OPEN);
        report.setReporter(reporter);
        report.setCategory(IncidentCategory.FRAUD);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(report));
        when(incidentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var info = incidentService.resolve(admin, 5L, IncidentStatus.RESOLVED, "Remboursement effectué");

        assertThat(info.status()).isEqualTo("RESOLVED");
        assertThat(report.getResolvedBy()).isEqualTo(admin);
        assertThat(report.getResolvedAt()).isNotNull();
        verify(auditLogService).log(eq(admin), eq(AuditAction.INCIDENT_STATUS_CHANGED), eq("IncidentReport"), eq(5L), anyString());
    }
}
