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
// eux-memes (WithdrawalService, TontineService), jamais depuis les controleurs, pour garantir
// qu'une entree n'est ecrite que si l'action a reellement abouti.
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void log(User actor, AuditAction action, String targetType, Long targetId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActor(actor);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetails(details);
        auditLogRepository.save(entry);
    }

    public List<AuditLogInfo> list() {
        return auditLogRepository.findTop200ByOrderByIdDesc().stream().map(e -> new AuditLogInfo(
                e.getId(), e.getActor().getFullName(), e.getAction().name(),
                e.getTargetType(), e.getTargetId(), e.getDetails(), e.getCreatedAt()
        )).toList();
    }
}
