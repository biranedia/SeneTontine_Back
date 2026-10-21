package com.senetontine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SavingsEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "goal_id")
    private SavingsGoal goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavingsEntryType type;

    // Toujours positif ; le sens est donne par `type`.
    @Column(nullable = false)
    private BigDecimal amount;

    @Column(length = 120)
    private String note;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
