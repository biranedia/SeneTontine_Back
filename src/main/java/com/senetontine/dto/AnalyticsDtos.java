package com.senetontine.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    // value = periode courante, previous = periode precedente de meme duree (pour le delta).
    public record Kpi(BigDecimal value, BigDecimal previous) {}

    public record DayPoint(LocalDate day, BigDecimal value) {}

    public record RecentRevenue(Long id, String userName, String purpose, BigDecimal amount, Instant paidAt) {}

    public record Analytics(
            int days,
            Kpi revenue, Kpi signups, Kpi tontinesCreated, Kpi cotisationsVolume,
            long totalUsers, long pendingDeclarations, long openIncidents,
            Map<String, Long> tontinesByStatus,
            List<DayPoint> revenueSeries, List<DayPoint> signupSeries,
            List<DayPoint> tontineSeries, List<DayPoint> cotisationSeries,
            List<RecentRevenue> recentRevenue) {}
}
