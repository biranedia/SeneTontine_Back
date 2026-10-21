package com.senetontine.service;

import com.senetontine.dto.AnnouncementDtos.AnnouncementInfo;
import com.senetontine.dto.AnnouncementDtos.BroadcastRequest;
import com.senetontine.model.Announcement;
import com.senetontine.model.AuditAction;
import com.senetontine.model.User;
import com.senetontine.model.UserRole;
import com.senetontine.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

// Diffusion d'annonces plateforme : ecriture (broadcast/history) reservee aux admins, lecture
// de la derniere annonce (latest) ouverte a tout utilisateur connecte - c'est precisement le
// public vise par une annonce.
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final AuditLogService auditLogService;

    public Optional<AnnouncementInfo> latest() {
        return announcementRepository.findTopByOrderByIdDesc().map(this::toInfo);
    }

    public List<AnnouncementInfo> history(User caller) {
        requireAdmin(caller);
        return announcementRepository.findTop20ByOrderByIdDesc().stream().map(this::toInfo).toList();
    }

    public AnnouncementInfo broadcast(User caller, BroadcastRequest req) {
        requireAdmin(caller);
        Announcement a = new Announcement();
        a.setMessage(req.message().trim());
        a.setCreatedBy(caller);
        Announcement saved = announcementRepository.save(a);
        auditLogService.log(caller, AuditAction.ANNOUNCEMENT_BROADCAST, "Announcement", saved.getId(), saved.getMessage());
        return toInfo(saved);
    }

    private AnnouncementInfo toInfo(Announcement a) {
        return new AnnouncementInfo(a.getId(), a.getMessage(), a.getCreatedBy().getFullName(), a.getCreatedAt().toString());
    }

    private void requireAdmin(User user) {
        if (user.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
    }
}
