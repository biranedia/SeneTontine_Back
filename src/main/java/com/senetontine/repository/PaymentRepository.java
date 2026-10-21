package com.senetontine.repository;

import com.senetontine.model.Payment;
import com.senetontine.model.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByTontineIdOrderByRoundNumberAsc(Long tontineId);
    List<Payment> findByUserIdAndStatus(Long userId, PaymentStatus status);
    List<Payment> findByUserIdAndStatusOrderByPaidAtDesc(Long userId, PaymentStatus status);
    Optional<Payment> findByTontineIdAndUserIdAndRoundNumber(Long tontineId, Long userId, Integer roundNumber);
    long countByUserIdAndStatus(Long userId, PaymentStatus status);
    long countByStatus(PaymentStatus status);
    long countByTontineIdAndRoundNumberAndStatus(Long tontineId, Integer roundNumber, PaymentStatus status);

    // Argent total reellement mobilise sur la plateforme, toutes tontines et tous utilisateurs
    // confondus - utilise par le tableau de bord admin. coalesce(...,0) : sans paiement PAID du
    // tout (plateforme flambant neuve), SUM() renverrait NULL plutot que 0.
    @Query("select coalesce(sum(p.amount + p.penaltyAmount), 0) from Payment p where p.status in ('PAID', 'LATE')")
    BigDecimal sumAllPaidAmounts();
}
