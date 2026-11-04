package com.senetontine.model;

public enum AuditAction {
    WITHDRAWAL_APPROVED,
    WITHDRAWAL_REJECTED,
    SUPERVISOR_PROMOTED,
    SUPERVISOR_REVOKED,
    // Gestion des comptes par un admin plateforme (voir AdminService).
    USER_BLOCKED,
    USER_UNBLOCKED,
    USER_ROLE_CHANGED,
    // Traitement d'un signalement d'incident (voir IncidentService).
    INCIDENT_STATUS_CHANGED,
    // Supervision plateforme etendue (voir AdminService).
    TONTINE_STATUS_CHANGED,
    MESSAGE_DELETED,
    ANNOUNCEMENT_BROADCAST,
    // Mot de passe temporaire genere par l'admin (voir AdminAccountService).
    PASSWORD_RESET,
    USER_DELETED,
    USER_UPDATED,
    CREDIT_GRANTED,
    // Connexion reussie d'un compte ADMIN : permet de detecter un acces non autorise
    // a un compte administrateur (depuis une IP inconnue, a une heure inhabituelle...).
    ADMIN_LOGIN
}
