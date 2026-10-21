package com.senetontine.repository;

import com.senetontine.model.Tontine;
import com.senetontine.model.TontineStatus;
import com.senetontine.model.TontineVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TontineRepository extends JpaRepository<Tontine, Long> {

    @Query("select t from Tontine t join TontineMember m on m.tontine = t where m.user.id = :userId")
    List<Tontine> findAllForUser(@Param("userId") Long userId);

    Optional<Tontine> findByInviteCode(String inviteCode);

    boolean existsByInviteCode(String inviteCode);

    // "Decouvrir des tontines" ne montre que celles encore en recrutement : une tontine deja
    // ACTIVE (ou terminee) n'a plus de sens a rejoindre. Plafonne comme toutes les autres
    // listes de l'app pour eviter une liste illimitee si beaucoup de tontines publiques existent.
    List<Tontine> findTop50ByVisibilityAndStatusOrderByIdDesc(TontineVisibility visibility, TontineStatus status);

    // Tableau de bord admin (voir AdminService) : compte total par statut, toute la plateforme.
    long countByStatus(TontineStatus status);

    // Supervision admin (AdminService.listTontines) : TOUTES les tontines de la plateforme,
    // pas seulement celles de l'appelant - plafonnee comme les autres listes admin.
    List<Tontine> findTop200ByOrderByIdDesc();

    // Rappels de cotisation (voir PaymentReminderService) : tontines actives dont l'echeance
    // du tour en cours tombe exactement a la date cible (ex: dans 3 jours).
    List<Tontine> findByStatusAndNextTurnDate(TontineStatus status, java.time.LocalDate nextTurnDate);

    // Nombre de tontines creees par un utilisateur : la toute premiere est offerte.
    long countByCreatedById(Long userId);

    boolean existsByRenewedFromId(Long tontineId);
}
