package com.senetontine.repository;

import com.senetontine.model.TontineMember;
import com.senetontine.model.TontineStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TontineMemberRepository extends JpaRepository<TontineMember, Long> {
    List<TontineMember> findByTontineId(Long tontineId);
    List<TontineMember> findByUserId(Long userId);
    Optional<TontineMember> findByTontineIdAndUserId(Long tontineId, Long userId);
    long countByTontineId(Long tontineId);
    long countByUserIdAndTontine_Status(Long userId, TontineStatus status);

    // Projection utilisee par AdminService.listTontines()/exportTontinesCsv() pour compter les
    // membres de PLUSIEURS tontines en une seule requete (GROUP BY), plutot qu'un
    // countByTontineId() par tontine dans une boucle - jusqu'a 200 requetes en plus sur
    // /api/admin/tontines sinon, un vrai probleme de performance a mesure que la plateforme
    // grossit.
    interface TontineMemberCount {
        Long getTontineId();
        long getCnt();
    }

    @Query("select tm.tontine.id as tontineId, count(tm) as cnt from TontineMember tm where tm.tontine.id in :tontineIds group by tm.tontine.id")
    List<TontineMemberCount> countGroupedByTontineIds(@Param("tontineIds") List<Long> tontineIds);

}
