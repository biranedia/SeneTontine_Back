package com.senetontine.repository;

import java.time.Instant;
import com.senetontine.model.CreatorPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreatorPaymentRepository extends JpaRepository<CreatorPayment, Long> {

    // Verrou pessimiste : un webhook et une verification manuelle simultanes sur la meme
    // commande ne peuvent jamais crediter deux fois.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CreatorPayment p where p.orderId = :orderId")
    Optional<CreatorPayment> lockByOrderId(@Param("orderId") String orderId);

    Optional<CreatorPayment> findByOrderId(String orderId);

    // Droits disponibles (payes, non consommes), plus ancien d'abord. Deux requetes distinctes
    // plutot qu'un parametre facultatif ":tontineId is null", que PostgreSQL rejette parfois
    // ("could not determine data type of parameter").
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CreatorPayment p where p.user.id = :userId and p.purpose = com.senetontine.model.CreatorPayment.Purpose.CREATE "
            + "and p.status = com.senetontine.model.CreatorPayment.Status.PAID and p.consumedAt is null order by p.id")
    List<CreatorPayment> lockAvailableCreationRights(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CreatorPayment p where p.user.id = :userId and p.purpose = com.senetontine.model.CreatorPayment.Purpose.RENEW "
            + "and p.tontine.id = :tontineId and p.status = com.senetontine.model.CreatorPayment.Status.PAID and p.consumedAt is null order by p.id")
    List<CreatorPayment> lockAvailableRenewalRights(@Param("userId") Long userId, @Param("tontineId") Long tontineId);

    List<CreatorPayment> findTop50ByUserIdOrderByIdDesc(Long userId);

    List<CreatorPayment> findByStatusAndPaidAtGreaterThanEqual(CreatorPayment.Status status, Instant from);

    List<CreatorPayment> findTop8ByStatusOrderByPaidAtDesc(CreatorPayment.Status status);

}
