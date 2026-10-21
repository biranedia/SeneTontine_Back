package com.senetontine.dto;

import java.time.Instant;

public class AuditLogDtos {

    public record AuditLogInfo(
            Long id,
            String actorName,
            String action,
            String targetType,
            Long targetId,
            String details,
            Instant createdAt
    ) {}
}
