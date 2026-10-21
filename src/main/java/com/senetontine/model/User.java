package com.senetontine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "phone"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.MEMBER;


    @Column(nullable = false)
    private BigDecimal trustScore = new BigDecimal("5.0");

    // Photo de profil, encodee en base64, choisie depuis l'ecran profil (voir
    // UserController.updateAvatar).
    // columnDefinition = "TEXT" (et PAS @Lob) : sur PostgreSQL, @Lob mappe un String vers le
    // type "oid" (large object stocke a part, avec sa propre gestion de cycle de vie) alors que
    // la migration V9 cree une colonne "text" classique - @Lob provoquait une erreur de
    // validation de schema au demarrage ("wrong column type ... found [text], expecting [oid]").
    // Un TEXT Postgres accepte largement une image encodee en base64 (jusqu'a 1 Go), donc pas
    // besoin du mecanisme large-object de @Lob ici.
    @Column(columnDefinition = "TEXT")
    private String avatarBase64;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    // Suspension de compte par un admin (voir AdminService.setActive) - fraude suspectee,
    // litige en cours... TRUE par defaut (voir V11) : ne bloque jamais personne
    // retroactivement, uniquement les comptes explicitement suspendus depuis. Verifie a la
    // fois a la connexion (AuthService.login) ET a CHAQUE requete authentifiee
    // (TokenAuthFilter) - sans cette deuxieme verification, un token deja emis avant le
    // blocage resterait utilisable jusqu'a son expiration naturelle (jusqu'a 30 jours).
    @Column(nullable = false)
    private boolean active = true;

    // Horodatage de l'acceptation des CGU au moment de l'inscription (voir
    // AuthService.register). NULL pour les comptes crees avant l'introduction de cet ecran de
    // consentement - jamais retrofit retroactivement.
    private Instant tosAcceptedAt;

}
