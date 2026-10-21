package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Paiement d'un createur (via NabooPay) donnant le droit de creer une tontine ou d'en
// renouveler une. Un paiement PAID et non encore consomme (consumedAt null) = un droit
// disponible. Seul BillingService fait evoluer ces lignes.
@Entity
@Getter
@Setter
@NoArgsConstructor
public class CreatorPayment {

    public enum Purpose { CREATE, RENEW }
    public enum Status { PENDING, PAID, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @ManyToOne
    @JoinColumn(name = "tontine_id")
    private Tontine tontine;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(unique = true, length = 100)
    private String orderId;

    @Column(length = 1000)
    private String checkoutUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    private Instant consumedAt;

    private Instant paidAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
