package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Trace immuable des actions sensibles effectuees par un admin plateforme ou un superviseur de
// tontine (voir AuditAction) : qui a fait quoi, sur quoi, quand. Jamais modifie ni supprime apres
// creation - c'est la garantie que la trace reste fiable en cas de litige ou d'enquete. Ne
// capture pas encore l'adresse IP de l'appelant (necessiterait de faire transiter la
// HttpServletRequest depuis les controleurs jusqu'aux services) : ameliorations possible avant
// une vraie mise en production, pas indispensable en mode simulation.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(nullable = false)
    private String targetType;

    @Column(nullable = false)
    private Long targetId;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
