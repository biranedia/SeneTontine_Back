package com.senetontine.model;

public enum AuditAction {
    WITHDRAWAL_APPROVED,
    WITHDRAWAL_REJECTED,
    SUPERVISOR_PROMOTED,
    SUPERVISOR_REVOKED,
    // Gestion des comptes par un admin plateforme (voir AdminService) - bloquer/debloquer un
    // compte ou changer son role sont des actions sensibles qui meritent une trace, au meme
    // titre que les decisions sur les retraits.
    USER_BLOCKED,
    USER_UNBLOCKED,
    USER_ROLE_CHANGED,
    // Traitement d'un signalement d'incident soumis par un membre (voir IncidentService).
    INCIDENT_STATUS_CHANGED,
    // Supervision plateforme etendue (voir AdminService) : annuler une tontine problematique,
    // moderer/supprimer un message de chat signale, ou diffuser une annonce a tous les
    // utilisateurs sont toutes des actions a fort impact qui doivent rester tracables.
    TONTINE_STATUS_CHANGED,
    MESSAGE_DELETED,
    ANNOUNCEMENT_BROADCAST,
    // Mot de passe oublie : l'admin genere un mot de passe temporaire (voir AdminAccountService).
    PASSWORD_RESET
}
