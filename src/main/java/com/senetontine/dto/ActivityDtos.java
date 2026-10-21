package com.senetontine.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class ActivityDtos {

    // Un evenement dans l'historique personnel de l'utilisateur : soit une cotisation
    // payee (montant negatif du point de vue de l'utilisateur), soit un pot recu en tant
    // que beneficiaire d'un tour (montant positif).
    public record ActivityItem(
            String type,      // "CONTRIBUTION" ou "PAYOUT"
            String label,
            BigDecimal amount, // signe : negatif pour une cotisation payee, positif pour un pot recu
            Instant date
    ) {}

    public record UserStats(
            int activeTontines,
            BigDecimal totalContributed,
            int paymentsCount
    ) {}
}
