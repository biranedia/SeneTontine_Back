package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

// Versement du pot d'un tour a son beneficiaire. L'argent ne transite pas par l'app : le
// createur, qui a recu les cotisations sur son Wave/OM, envoie le pot lui-meme puis le marque
// "envoye" ; le beneficiaire confirme ensuite "recu". Les deux dates restent visibles du groupe.
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"tontine_id", "round_number"}))
@Getter
@Setter
@NoArgsConstructor
public class PotPayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tontine_id")
    private Tontine tontine;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @ManyToOne(optional = false)
    @JoinColumn(name = "beneficiary_id")
    private User beneficiary;

    @Column(nullable = false)
    private BigDecimal amount;

    private Instant sentAt;

    private Instant receivedAt;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
