package com.senetontine.repository;

import com.senetontine.model.SavingsEntry;
import com.senetontine.model.SavingsEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SavingsEntryRepository extends JpaRepository<SavingsEntry, Long> {
    List<SavingsEntry> findTop100ByGoalIdOrderByIdDesc(Long goalId);

    @Query("select coalesce(sum(e.amount), 0) from SavingsEntry e where e.goal.id = :goalId and e.type = :type")
    BigDecimal sumByGoalAndType(@Param("goalId") Long goalId, @Param("type") SavingsEntryType type);
}
