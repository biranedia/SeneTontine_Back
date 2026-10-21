package com.senetontine.model;

public enum TontineStatus {
    ACTIVE,
    PENDING, // en attente de recrutement, pas encore demarree
    // Annulee par un admin (litige, fraude averee, tontine bloquee sans issue...). Conserve
    // l'historique (membres, paiements deja effectues) plutot que de supprimer la tontine, mais
    // la retire de toute liste active/decouvrable cote membres. Voir AdminService.setTontineStatus.
    CANCELLED,
    // Tous les tours ont ete cotises et confirmes : chaque membre a eu son tour de beneficiaire.
    COMPLETED
}
