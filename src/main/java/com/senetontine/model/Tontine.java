package com.senetontine.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Tontine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String emoji = "🤝";

    @Column(nullable = false)
    private BigDecimal cotisationAmount;

    @Column(nullable = false)
    private Integer membersRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RotationType rotationType = RotationType.RANDOM;

    private BigDecimal penaltyPerDay = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TontineStatus status = TontineStatus.PENDING;

    // tour en cours (1-indexe). Avance quand tous les membres ont paye leur cotisation du tour.
    @Column(nullable = false)
    private Integer currentRound = 1;

    // Date d'echeance de la cotisation du tour en cours. Positionnee quand la tontine devient
    // ACTIVE (voir TontineService.join) et repoussee a chaque nouveau tour (voir
    // PaymentService.advanceRoundIfComplete). C'est cette date qui sert de reference pour
    // calculer les penalites de retard (voir Tontine.cycleDurationDays et PaymentService.pay).
    private LocalDate nextTurnDate;

    // Duree d'un cycle de cotisation en jours (mensuel par defaut). Pas encore expose dans
    // l'ecran de creation de tontine cote front - valeur par defaut raisonnable pour l'instant,
    // le champ existe pour permettre une personnalisation future sans nouvelle migration.
    @Column(nullable = false)
    private Integer cycleDurationDays = 30;

    // Code court partageable (ex: "TONT-4X7K") permettant de rejoindre cette tontine sans en
    // connaitre l'ID technique. Genere une fois a la creation (voir TontineService.create /
    // generateUniqueInviteCode), jamais modifie ensuite. Sert de base a l'invitation par
    // WhatsApp/SMS (le lien "clic direct" necessiterait un domaine + des liens universels que
    // l'app n'a pas encore) et a l'ajout direct par numero de telephone reutilise la meme
    // logique d'ajout (voir TontineService.addMember).
    @Column(nullable = false, unique = true)
    private String inviteCode;

    // PRIVATE par defaut : rejoignable seulement via inviteCode ou ajout direct par le
    // createur. PUBLIC : en plus decouvrable par n'importe qui dans "Decouvrir des tontines"
    // (voir TontineService.discover). Choisi une fois a la creation (voir CreateTontineRequest).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TontineVisibility visibility = TontineVisibility.PRIVATE;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // Dernier tour pour lequel un rappel de cotisation a deja ete envoye (voir
    // PaymentReminderService) - evite d'envoyer plusieurs fois le meme rappel si le job
    // planifie tourne plusieurs fois avant que le tour n'avance. NULL = jamais envoye.
    private Integer reminderSentRound;

    // Numeros du createur sur lesquels les membres envoient leur cotisation (au moins un des
    // deux est exige, voir TontineService). L'app ne fait transiter aucun argent.
    @Column(length = 30)
    private String wavePhone;

    @Column(length = 30)
    private String orangeMoneyPhone;

    // Tontine terminee dont celle-ci est le renouvellement (voir TontineService.renew).
    @OneToOne
    @JoinColumn(name = "renewed_from_id", unique = true)
    private Tontine renewedFrom;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}
