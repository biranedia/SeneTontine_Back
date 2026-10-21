package com.senetontine.dto;

import java.time.Instant;

public class NotificationDtos {

    public record NotificationInfo(
            Long id,
            String type,
            String title,
            String body,
            boolean read,
            Instant createdAt
    ) {}

    public record UnreadCount(long count) {}
}
