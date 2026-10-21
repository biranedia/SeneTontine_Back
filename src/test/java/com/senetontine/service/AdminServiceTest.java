package com.senetontine.service;

import com.senetontine.model.*;
import com.senetontine.repository.IncidentReportRepository;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import com.senetontine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Tableau de bord + gestion de plateforme (voir AdminService) : reserve aux admins, aucune
// notion de role intermediaire. Couvre surtout les garde-fous d'auto-protection (un admin ne
// peut ni se bloquer, ni bloquer un pair sans le retrograder d'abord, ni retrograder le dernier
// admin restant) et la regression sur le correctif N+1 du comptage de membres.
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TontineRepository tontineRepository;
    @Mock private TontineMemberRepository tontineMemberRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private IncidentReportRepository incidentReportRepository;
    @Mock private AuditLogService auditLogService;

    private AdminService adminService;

    private User admin;
    private User otherAdmin;
    private User member;

    @BeforeEach
    void setUp() {
        adminService = new AdminService(userRepository, tontineRepository, tontineMemberRepository,
                paymentRepository, incidentReportRepository, auditLogService);

        admin = new User();
        admin.setId(1L);
        admin.setRole(UserRole.ADMIN);

        otherAdmin = new User();
        otherAdmin.setId(2L);
        otherAdmin.setRole(UserRole.ADMIN);
        otherAdmin.setFullName("Autre Admin");
        otherAdmin.setPhone("+221770000002");

        member = new User();
        member.setId(3L);
        member.setRole(UserRole.MEMBER);
        member.setFullName("Membre Ordinaire");
        member.setPhone("+221770000003");
    }

    @Test
    void getOverview_rejectsNonAdmin() {
        assertThatThrownBy(() -> adminService.getOverview(member))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(userRepository, tontineRepository, paymentRepository);
    }

    @Test
    void setActive_rejectsSelfBlock() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> adminService.setActive(admin, 1L, false))
                .isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setActive_rejectsBlockingAnotherAdminDirectly() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));
        assertThatThrownBy(() -> adminService.setActive(admin, 2L, false))
                .isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setActive_blocksPlainMemberAndAuditLogs() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(member));

        adminService.setActive(admin, 3L, false);

        assertThat(member.isActive()).isFalse();
        verify(userRepository).save(member);
        verify(auditLogService).log(eq(admin), eq(AuditAction.USER_BLOCKED), eq("User"), eq(3L), anyString());
    }

    @Test
    void setRole_rejectsSelfRoleChange() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        assertThatThrownBy(() -> adminService.setRole(admin, 1L, UserRole.MEMBER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void setRole_rejectsDemotingLastRemainingAdmin() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countByRole(UserRole.ADMIN)).thenReturn(1L);

        assertThatThrownBy(() -> adminService.setRole(admin, 2L, UserRole.MEMBER))
                .isInstanceOf(ResponseStatusException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void setRole_promotesMemberAndAuditLogs() {
        when(userRepository.findById(3L)).thenReturn(Optional.of(member));

        adminService.setRole(admin, 3L, UserRole.ADMIN);

        assertThat(member.getRole()).isEqualTo(UserRole.ADMIN);
        verify(auditLogService).log(eq(admin), eq(AuditAction.USER_ROLE_CHANGED), eq("User"), eq(3L), anyString());
    }

    @Test
    void setTontineStatus_updatesStatusAndAuditLogs() {
        Tontine tontine = new Tontine();
        tontine.setId(20L);
        tontine.setName("Test Tontine");
        tontine.setStatus(TontineStatus.ACTIVE);
        tontine.setMembersRequired(4);
        tontine.setCreatedBy(member);
        when(tontineRepository.findById(20L)).thenReturn(Optional.of(tontine));
        when(tontineMemberRepository.countByTontineId(20L)).thenReturn(4L);

        var info = adminService.setTontineStatus(admin, 20L, TontineStatus.CANCELLED);

        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.CANCELLED);
        assertThat(info.status()).isEqualTo("CANCELLED");
        verify(auditLogService).log(eq(admin), eq(AuditAction.TONTINE_STATUS_CHANGED), eq("Tontine"), eq(20L), anyString());
    }



    // Verrouille le correctif N+1 : listTontines() doit compter les membres de TOUTES les
    // tontines en UNE seule requete groupee, jamais un countByTontineId() par tontine dans la
    // boucle (ce qui ferait explicitement jusqu'a 200 requetes SQL supplementaires ici).
    @Test
    void listTontines_countsMembersInOneGroupedQuery_neverPerTontine() {
        Tontine t1 = new Tontine();
        t1.setId(1L);
        t1.setMembersRequired(4);
        t1.setCreatedBy(member);
        Tontine t2 = new Tontine();
        t2.setId(2L);
        t2.setMembersRequired(4);
        t2.setCreatedBy(member);
        when(tontineRepository.findTop200ByOrderByIdDesc()).thenReturn(List.of(t1, t2));
        when(tontineMemberRepository.countGroupedByTontineIds(List.of(1L, 2L))).thenReturn(List.of());

        adminService.listTontines(admin);

        verify(tontineMemberRepository).countGroupedByTontineIds(List.of(1L, 2L));
        verify(tontineMemberRepository, never()).countByTontineId(any());
    }
}
