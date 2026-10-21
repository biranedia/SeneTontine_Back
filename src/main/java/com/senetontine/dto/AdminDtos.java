package com.senetontine.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public class AdminDtos {

    // Vue d'ensemble de toute la plateforme, reservee aux comptes ADMIN (voir
    // AdminService.getOverview) - jamais d'id utilisateur/tontine specifique ici, uniquement
    // des agregats, donc aucun risque de fuite de donnees personnelles via cet ecran.
    public record PlatformOverview(
            long totalUsers,
            long totalTontines,
            long activeTontines,
            long pendingTontines,
            // Somme des cotisations CONFIRMEES par les createurs (suivies, jamais detenues).
            BigDecimal totalMoneyTracked,
            long pendingDeclarations,
            long totalPaymentsMade,
            long openIncidents
    ) {}

    // Une ligne de la liste/recherche admin des utilisateurs. Le numero de telephone est
    // inclus (contrairement a MemberInfo cote tontine, qui ne l'expose jamais) : un admin en a
    // legitimement besoin pour identifier/contacter un utilisateur en cas de litige - c'est
    // precisement le niveau d'acces qui justifie que ce role soit lui-meme si bien protege.
    public record AdminUserInfo(
            Long id,
            String fullName,
            String phone,
            String role,
            boolean active,
            String createdAt
    ) {}

    public record UpdateActiveRequest(
            @NotNull Boolean active
    ) {}

    public record UpdateRoleRequest(
            @NotNull String role // "MEMBER" ou "ADMIN"
    ) {}

    // Supervision admin : une tontine de la plateforme, avec juste assez d'infos pour reperer
    // un probleme (montant anormal, nombre de membres, statut bloque) sans avoir a ouvrir
    // chaque tontine une par une.
    public record AdminTontineInfo(
            Long id,
            String name,
            String emoji,
            String status,
            int membersCount,
            int membersRequired,
            BigDecimal cotisationAmount,
            String createdByName,
            String createdAt
    ) {}

    public record UpdateTontineStatusRequest(
            @NotNull String status // "ACTIVE", "PENDING", "CANCELLED" ou "COMPLETED"
    ) {}

    // Une tontine a laquelle un utilisateur donne appartient, vue depuis sa fiche admin.
    public record TontineMembershipItem(
            Long tontineId,
            String tontineName,
            String emoji,
            String role
    ) {}

    // Une cotisation payee par un utilisateur donne, vue depuis sa fiche admin.
    public record PaymentHistoryItem(
            Long tontineId,
            String tontineName,
            int roundNumber,
            BigDecimal amount,
            BigDecimal penaltyAmount,
            String status,
            String paidAt
    ) {}

    // Fiche complete d'un utilisateur, ouverte depuis la liste admin/users - reunit en un seul
    // appel tout ce dont un admin a besoin pour instruire un litige (ses tontines, son
    // historique de paiements, le nombre de signalements qu'il a soumis) plutot que de le
    // faire naviguer entre plusieurs ecrans.
    public record UserDetailInfo(
            AdminUserInfo user,
            List<TontineMembershipItem> tontines,
            List<PaymentHistoryItem> payments,
            long incidentsCount
    ) {}

}
