package com.senetontine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AnnouncementDtos {

    public record AnnouncementInfo(
            Long id,
            String message,
            String createdByName,
            String createdAt
    ) {}

    public record BroadcastRequest(
            @NotBlank @Size(min = 5, max = 1000) String message
    ) {}
}
