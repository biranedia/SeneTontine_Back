package com.senetontine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Annonce diffusee par un admin a TOUS les utilisateurs de la plateforme (maintenance prevue,
// nouvelle fonctionnalite, alerte de securite...). Pas de vrai push (infrastructure absente pour
// l'instant) : chaque client recupere la derniere annonce au chargement du dashboard (voir
// AnnouncementController.latest), un peu comme une banniere - suffisant tant que l'app tourne
// au premier plan, et sans le cout/la complexite d'un vrai service de notifications push.
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String message;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
