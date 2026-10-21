package com.senetontine.model;

public enum NotificationType {
    // Anciennes valeurs (circuit de retrait interne retire) : conservees uniquement pour que les
    // notifications deja stockees en base restent lisibles. Plus jamais emises.
    WITHDRAWAL_APPROVED,
    WITHDRAWAL_REJECTED,
    PAYOUT_RECEIVED,

    ROUND_COMPLETED,
    ADDED_TO_TONTINE,
    PAYMENT_REMINDER,
    MEMBER_LEFT,
    // Cycle declaration -> confirmation (voir PaymentService).
    PAYMENT_DECLARED,
    PAYMENT_CONFIRMED,
    PAYMENT_REJECTED,
    // Versement du pot par le createur au beneficiaire du tour.
    POT_TO_SEND,
    POT_SENT,
    POT_RECEIVED,
    TONTINE_COMPLETED,
    // Epargne individuelle.
    SAVINGS_REMINDER,
    SAVINGS_GOAL_REACHED,
    // Abonnement createur.
    SUBSCRIPTION_ACTIVATED, // ancien abonnement Google, plus emis
    CREATOR_PAYMENT_CONFIRMED,
    TONTINE_RENEWED
}
