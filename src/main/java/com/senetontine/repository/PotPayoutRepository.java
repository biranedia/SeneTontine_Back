package com.senetontine.repository;

import com.senetontine.model.PotPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PotPayoutRepository extends JpaRepository<PotPayout, Long> {
    Optional<PotPayout> findByTontineIdAndRoundNumber(Long tontineId, Integer roundNumber);
    List<PotPayout> findByTontineIdOrderByRoundNumberAsc(Long tontineId);
    List<PotPayout> findByBeneficiaryIdOrderByIdDesc(Long beneficiaryId);
}
