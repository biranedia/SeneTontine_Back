package com.senetontine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// On ne stocke JAMAIS le token en clair : seulement le hash SHA-256 de sa valeur.
// Si la base fuite, les tokens d'acces ne sont pas directement reutilisables.
// Chaque connexion cree un nouveau AuthToken (multi-session : plusieurs appareils possibles),
// et un token peut etre revoque individuellement (ex: "deconnecter cet appareil").
//
// Session glissante (alternative pragmatique a un JWT + refresh token complet, cf.
// TokenAuthFilter et AuthService) : expiresAt est repousse a chaque requete authentifiee
// (voir lastUsedAt), donc un utilisateur actif n'est jamais deconnecte brutalement, mais un
// token inactif finit par expirer naturellement. Une duree de vie absolue (createdAt + 30j)
// force malgre tout une reconnexion periodique meme pour un utilisateur tres actif, ce qui
// limite la duree d'exploitation d'un token vole. Le nombre de sessions actives par
// utilisateur est plafonne dans AuthService.issueToken().
@Entity
@Table(indexes = @Index(columnList = "tokenHash", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuthToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Mis a jour a chaque requete authentifiee reussie (voir TokenAuthFilter). Sert a la
    // fois d'indicateur d'activite et de base pour la fenetre glissante d'expiration.
    @Column(nullable = false)
    private Instant lastUsedAt = Instant.now();
}
