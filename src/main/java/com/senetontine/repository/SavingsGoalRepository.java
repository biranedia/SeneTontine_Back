package com.senetontine.repository;

import com.senetontine.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUserIdOrderByIdDesc(Long userId);
    List<SavingsGoal> findByNextDueDateAndCompletedAtIsNull(LocalDate nextDueDate);
}
