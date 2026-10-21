package com.senetontine.dto;

import java.math.BigDecimal;

public class StatsDtos {

    // Une entree "repartition par tontine" pour l'ecran Statistiques : juste ce qu'il faut
    // pour afficher une barre de progression par tontine, sans refaire un appel /tontines/{id}
    // par ligne cote client.
    public record TontineBreakdown(
            Long id,
            String name,
            String emoji,
            double progress
    ) {}

    public record UserStatsInfo(
            // Somme de tous les paiements REELLEMENT effectues (cotisation + penalite eventuelle)
            // toutes tontines confondues, depuis le debut - pas un solde de portefeuille (l'argent
            // part vers le pot de la tontine, il ne reste pas "chez" l'utilisateur).
            BigDecimal totalSaved,
            // Meme somme, mais limitee au mois calendaire en cours - sert a afficher
            // "+X FCFA ce mois" comme indicateur d'activite recente.
            BigDecimal monthGain,
            long activeTontines,
            long pendingTontines,
            long paymentsMade,
            BigDecimal trustScore,
            java.util.List<TontineBreakdown> breakdown
    ) {}
}
