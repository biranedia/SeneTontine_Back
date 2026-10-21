package com.senetontine.model;

public enum IncidentStatus {
    OPEN,         // vient d'etre soumis, pas encore pris en charge
    IN_PROGRESS,  // un admin l'a pris en charge, enquete en cours
    RESOLVED,     // traite, probleme regle
    DISMISSED     // examine mais rejete (infonde, doublon, hors-sujet...)
}
