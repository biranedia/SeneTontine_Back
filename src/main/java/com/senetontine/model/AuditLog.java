package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

// Trace immuable des actions sensibles effectuees par un admin plateforme ou un superviseur de
// tontine (voir AuditAction) : qui a fait quoi, sur quoi, quand, depuis quelle IP.
// Jamais modifie ni supprime apres creation - garantie d'integrite en cas de litige.
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

    // IPv4 ou IPv6 (max 45 car.) ; null pour les entrees anterieures a V10.
    @Column(length = 45)
    private String actorIp;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
