package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Epargne individuelle ("tontine solo") : un objectif personnel, suivi par l'utilisateur
// seul. Comme pour les tontines de groupe, l'app ne detient aucun argent : elle enregistre
// les versements declares et rappelle les echeances.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 16)
    private String emoji = "🎯";

    @Column(nullable = false)
    private BigDecimal targetAmount;

    // Versement prevu a chaque echeance (facultatif : epargne libre si null).
    private BigDecimal periodicAmount;

    private Integer cycleDays;

    private LocalDate nextDueDate;

    private Instant completedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
