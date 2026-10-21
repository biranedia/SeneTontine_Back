package com.senetontine.service;

import com.senetontine.dto.AnnouncementDtos.BroadcastRequest;
import com.senetontine.model.Announcement;
import com.senetontine.model.AuditAction;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.repository.AnnouncementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Diffusion d'annonces plateforme (voir AnnouncementService) : lecture ouverte a tous, ecriture
// reservee aux admins.
@ExtendWith(MockitoExtension.class)
class AnnouncementServiceTest {

    @Mock private AnnouncementRepository announcementRepository;
    @Mock private AuditLogService auditLogService;

    private AnnouncementService announcementService;

    private User member;
    private User admin;

    @BeforeEach
    void setUp() {
        announcementService = new AnnouncementService(announcementRepository, auditLogService);

        member = new User();
        member.setId(1L);
        member.setRole(UserRole.MEMBER);

        admin = new User();
        admin.setId(2L);
        admin.setFullName("Admin Plateforme");
        admin.setRole(UserRole.ADMIN);
    }

    @Test
    void latest_returnsEmptyWhenNoAnnouncementEverBroadcast() {
        when(announcementRepository.findTopByOrderByIdDesc()).thenReturn(Optional.empty());
        assertThat(announcementService.latest()).isEmpty();
    }

    @Test
    void history_rejectsNonAdmin() {
        assertThatThrownBy(() -> announcementService.history(member))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(announcementRepository);
    }

    @Test
    void broadcast_rejectsNonAdmin() {
        var req = new BroadcastRequest("Maintenance prévue dimanche soir");
        assertThatThrownBy(() -> announcementService.broadcast(member, req))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(announcementRepository);
    }

    @Test
    void broadcast_savesAndAuditLogsForAdmin() {
        when(announcementRepository.save(any())).thenAnswer(inv -> {
            Announcement a = inv.getArgument(0);
            a.setId(99L);
            return a;
        });
        var req = new BroadcastRequest("Maintenance prévue dimanche soir");

        var info = announcementService.broadcast(admin, req);

        assertThat(info.message()).isEqualTo("Maintenance prévue dimanche soir");
        assertThat(info.createdByName()).isEqualTo("Admin Plateforme");

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(admin);

        verify(auditLogService).log(eq(admin), eq(AuditAction.ANNOUNCEMENT_BROADCAST), eq("Announcement"), eq(99L), anyString());
    }
}
