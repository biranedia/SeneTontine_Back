package com.senetontine.dto;

import com.senetontine.model.RotationType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public class TontineDtos {

    // Numero senegalais (77/78/76/70/75...), avec ou sans +221/00221 et separateurs.
    public static final String SN_PHONE = "^(\\+221|00221)?[ -]?7[0-9]([ -]?[0-9]){7}$";
    public static final String SN_PHONE_MSG = "Numéro de téléphone sénégalais invalide (ex: 77 123 45 67)";

    public record TontineSummary(
            Long id,
            String name,
            String emoji,
            int members,
            String tourLabel,
            String cotisation,
            BigDecimal cotisationValue,
            String prochainTour,
            String beneficiaire,
            String status,
            double progress,
            // Statut de MA cotisation du tour en cours (due | declared | paid | late | none),
            // pour afficher directement sur la carte ce que l'utilisateur doit faire.
            String myStatus,
            boolean iAmCreator,
            // Declarations en attente de MA confirmation (si je suis le createur).
            int pendingConfirmations,
            // Versement de pot en attente me concernant : "SEND" (je suis le createur, pot
            // pas encore envoye), "RECEIVE" (je suis le beneficiaire, reception a confirmer)
            // ou null. Reste visible meme apres la fin de la tontine (dernier pot).
            String potAction,
            BigDecimal potAmount,
            String potBeneficiary
    ) {}

    public record MemberInfo(
            Long id,
            String name,
            String initials,
            String role,
            String status, // due | declared | paid | late
            boolean creator,
            Integer turnOrder,
            // Cotisation du tour en cours (null si rien de declare) : permet au createur de
            // confirmer/refuser directement depuis la liste des membres.
            Long paymentId,
            String paymentReference,
            String paymentMethod,
            BigDecimal amountDue,
            String avatarBase64
    ) {}

    public record TontineDetail(
            Long id,
            String name,
            String emoji,
            int members,
            String tourLabel,
            String cotisation,
            BigDecimal cotisationValue,
            String prochainTour,
            String beneficiaire,
            String status,
            double progress,
            String createdBy,
            String potTotal,
            List<MemberInfo> membersList,
            String inviteCode,
            Integer membersRequired,
            Integer currentRound,
            Integer cycleDurationDays,
            String rotationType,
            BigDecimal penaltyPerDay,
            boolean isPublic,
            String wavePhone,
            String orangeMoneyPhone,
            String startDate
    ) {}

    public record CreateTontineRequest(
            @NotBlank String name,
            @NotNull @Positive BigDecimal cotisationAmount,
            @NotNull @Min(2) @Max(50) Integer membersRequired,
            RotationType rotationType,
            @PositiveOrZero BigDecimal penaltyPerDay,
            Boolean makePublic,
            // Duree d'un tour en jours (7 = hebdomadaire, 30 = mensuel...). 30 par defaut.
            @Min(1) @Max(365) Integer cycleDurationDays,
            // Au moins un des deux est exige (verifie dans TontineService) : c'est la que les
            // membres envoient leur cotisation, l'app ne fait transiter aucun argent.
            @Pattern(regexp = "^$|" + SN_PHONE, message = SN_PHONE_MSG) String wavePhone,
            @Pattern(regexp = "^$|" + SN_PHONE, message = SN_PHONE_MSG) String orangeMoneyPhone,
            // Date du premier tour (facultative) : 1re échéance de cotisation et 1er versement.
            java.time.LocalDate startDate
    ) {}

    public record AddMemberByPhoneRequest(
            @NotBlank @Pattern(regexp = SN_PHONE, message = SN_PHONE_MSG) String phone
    ) {}

    public record JoinByCodeRequest(
            @NotBlank String inviteCode
    ) {}

    // settleWithCreator : le membre avait deja declare/paye sa cotisation du tour en cours ;
    // l'app ne detenant aucun argent, le remboursement se regle directement avec le createur.
    public record LeaveTontineResponse(
            boolean settleWithCreator,
            BigDecimal amountPaidThisRound
    ) {}
}
