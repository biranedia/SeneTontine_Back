package com.senetontine.dto;

import com.senetontine.model.IncidentCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class IncidentDtos {

    public record SubmitIncidentRequest(
            @NotNull IncidentCategory category,
            // Facultatifs : ex. "TONTINE" + l'id, si le signalement concerne une tontine
            // precise (pre-rempli cote app depuis l'ecran de cette tontine).
            String targetType,
            Long targetId,
            @NotBlank
            @Size(max = 2000, message = "Description trop longue (2000 caractères max)")
            String description
    ) {}

    public record ResolveIncidentRequest(
            @NotNull String status, // "IN_PROGRESS" | "RESOLVED" | "DISMISSED"
            @Size(max = 1000, message = "Note trop longue (1000 caractères max)")
            String adminNote
    ) {}

    public record IncidentInfo(
            Long id,
            String reporterName,
            String category,
            String targetType,
            Long targetId,
            String description,
            String status,
            String adminNote,
            String resolvedByName,
            String createdAt,
            String resolvedAt
    ) {}
}
