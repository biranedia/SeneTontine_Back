package com.senetontine.dto;

import com.senetontine.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public class PaymentDtos {

    public record PaymentInfo(
            Long id,
            Long tontineId,
            Long userId,
            String memberName,
            int roundNumber,
            BigDecimal amount,
            String method,
            String status, // DUE | DECLARED | PAID | LATE
            Instant declaredAt,
            Instant confirmedAt,
            String reference,
            BigDecimal penaltyAmount
    ) {}

    // Le membre declare avoir envoye sa cotisation au createur via Wave/OM, hors de l'app.
    public record DeclareRequest(
            @NotNull PaymentMethod method,
            @Size(max = 120, message = "Référence trop longue")
            String reference
    ) {}

    public record PotPayoutInfo(
            Long id,
            int roundNumber,
            Long beneficiaryId,
            String beneficiaryName,
            BigDecimal amount,
            Instant sentAt,
            Instant receivedAt
    ) {}
}
