package com.senetontine.model;

public enum IncidentCategory {
    FRAUD,           // fraude suspectee (faux paiement, identite usurpee...)
    PAYMENT_ISSUE,   // litige sur une cotisation ou un retrait
    HARASSMENT,      // comportement abusif d'un autre membre
    TECHNICAL_BUG,   // bug de l'application
    OTHER
}
