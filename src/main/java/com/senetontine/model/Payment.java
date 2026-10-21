package com.senetontine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

// Une cotisation due (ou payee) par un membre pour un tour donne d'une tontine
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tontine_id")
    private Tontine tontine;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false)
    private Integer roundNumber;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status = PaymentStatus.DUE;

    private LocalDate dueDate;

    private Instant paidAt;

    // Cycle declaration -> confirmation (voir PaymentService) : l'argent ne transite plus par
    // l'app, le membre declare son envoi Wave/OM puis le createur confirme l'avoir recu.
    private Instant declaredAt;

    private Instant confirmedAt;

    // Reference de transaction Wave/OM saisie par le membre (optionnelle) pour aider le
    // createur a retrouver le transfert.
    @Column(length = 120)
    private String reference;

    // Penalite de retard appliquee au moment du paiement (voir PaymentService.pay), calculee
    // a partir de Tontine.penaltyPerDay et du nombre de jours ecoules depuis nextTurnDate.
    // Conservee separement du montant de la cotisation de base pour garder une trace claire
    // de ce qui a ete preleve et pourquoi (transparence vis-a-vis du membre concerne).
    @Column(nullable = false)
    private BigDecimal penaltyAmount = BigDecimal.ZERO;
}
