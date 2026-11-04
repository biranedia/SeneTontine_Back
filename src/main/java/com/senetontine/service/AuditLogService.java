package com.senetontine.service;

import com.senetontine.dto.AuditLogDtos.AuditLogInfo;
import com.senetontine.model.AuditAction;
import com.senetontine.model.AuditLog;
import com.senetontine.model.User;
import com.senetontine.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// Journal d'audit des actions sensibles (voir AuditAction) : appele depuis les services metier
// eux-memes (AdminService, AuthService...), jamais depuis les controleurs, pour garantir
// qu'une entree n'est ecrite que si l'action a reellement abouti.
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    // Enregistre une action sans adresse IP (actions internes, planifiees, etc.)
    public void log(User actor, AuditAction action, String targetType, Long targetId, String details) {
        log(actor, action, targetType, targetId, details, null);
    }

    // Enregistre une action avec l'adresse IP de l'acteur (actions declenchees par une requete HTTP).
    public void log(User actor, AuditAction action, String targetType, Long targetId, String details, String actorIp) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        entry.setActorIp(actorIp);
        auditLogRepository.save(entry);
    }

    public List<AuditLogInfo> list() {
        return auditLogRepository.findTop200ByOrderByIdDesc().stream().map(e -> new AuditLogInfo(
                e.getId(),
                e.getActor().getFullName(),
                e.getAction().name(),
                e.getTargetType(),
                e.getTargetId(),
                e.getDetails(),
                e.getActorIp(),
                e.getCreatedAt()
        )).toList();
    }
}
