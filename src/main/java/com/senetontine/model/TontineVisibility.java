package com.senetontine.model;

public enum TontineVisibility {
    // Rejoignable uniquement via code d'invitation ou ajout direct par le createur/un admin
    // (voir TontineService.join / joinByCode / addMemberByPhone). C'est le defaut : une
    // tontine est par nature un cercle de confiance, pas un groupe ouvert a tout le monde.
    PRIVATE,
    // En plus des deux methodes ci-dessus, visible et rejoignable en un clic depuis l'ecran
    // "Decouvrir des tontines" par n'importe quel utilisateur verifie.
    PUBLIC
}
