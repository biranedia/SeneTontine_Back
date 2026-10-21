package com.senetontine.dto;

import com.senetontine.model.SavingsEntryType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public class SavingsDtos {

    public record CreateGoalRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 16) String emoji,
            @NotNull @Positive BigDecimal targetAmount,
            @Positive BigDecimal periodicAmount,
            @Min(1) @Max(365) Integer cycleDays
    ) {}

    public record EntryRequest(
            @NotNull SavingsEntryType type,
            @NotNull @Positive BigDecimal amount,
            @Size(max = 120) String note
    ) {}

    public record GoalSummary(
            Long id,
            String name,
            String emoji,
            BigDecimal targetAmount,
            BigDecimal savedAmount,
            double progress,
            BigDecimal periodicAmount,
            Integer cycleDays,
            LocalDate nextDueDate,
            boolean completed
    ) {}

    public record EntryInfo(
            Long id,
            String type,
            BigDecimal amount,
            String note,
            Instant createdAt
    ) {}

    public record GoalDetail(
            GoalSummary goal,
            List<EntryInfo> entries
    ) {}
}
