package com.senetontine.model;

public enum PaymentStatus {
    DUE,       // en attente
    DECLARED,  // le membre a declare avoir paye (Wave/OM hors app), en attente de confirmation du createur
    PAID,      // confirme par le createur, paye a temps
    LATE       // confirme par le createur, paye apres l'echeance
}
