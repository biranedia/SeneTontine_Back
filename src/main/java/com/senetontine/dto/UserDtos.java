package com.senetontine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class UserDtos {

    // Une photo de profil s'affiche en petit partout : plafond genereux sans etre illimite.
    private static final int MAX_AVATAR_BASE64_LENGTH = 4_000_000;

    public record ProfileInfo(
            Long userId,
            String fullName,
            String phone,
            String role,
            BigDecimal trustScore,
            String avatarBase64
    ) {}

    public record UpdateAvatarRequest(
            @NotBlank
            @Size(max = MAX_AVATAR_BASE64_LENGTH, message = "Photo trop volumineuse")
            String avatarBase64
    ) {}

    public record UpdateNameRequest(
            @NotBlank @Size(max = 80) String fullName
    ) {}

    // Mot de passe redemande avant une action irreversible.
    public record DeleteAccountRequest(
            @NotBlank String password
    ) {}
}
