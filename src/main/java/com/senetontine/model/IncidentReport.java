package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Signalement d'incident soumis par un membre, traite ensuite par un admin plateforme (voir
// IncidentService). Volontairement independant du systeme d'AuditLog (qui trace des actions
// deja effectuees par un admin/superviseur) : ceci est une entree cote MEMBRE, avant toute
// action admin - le traitement de ce signalement, lui, sera trace dans AuditLog.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentCategory category;

    // Cible facultative et non contrainte (voir migration V12) : "TONTINE", "USER", "PAYMENT"
    // ou null si le signalement ne concerne rien de precis.
    private String targetType;
    private Long targetId;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncidentStatus status = IncidentStatus.OPEN;

    @Column(length = 1000)
    private String adminNote;

    @ManyToOne
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant resolvedAt;
}
