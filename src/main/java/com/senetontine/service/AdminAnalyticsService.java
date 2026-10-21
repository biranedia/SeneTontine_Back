package com.senetontine.service;

import com.senetontine.dto.AnalyticsDtos.*;
import com.senetontine.model.*;
import com.senetontine.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.function.Function;

// Statistiques du tableau de bord admin : KPIs avec comparaison a la periode precedente et
// series journalieres (fuseau Dakar = UTC). Volume faible attendu : agregation en memoire.
@Service
@RequiredArgsConstructor
public class AdminAnalyticsService {

    private static final ZoneId ZONE = ZoneId.of("Africa/Dakar");

    private final UserRepository userRepository;
    private final TontineRepository tontineRepository;
    private final PaymentRepository paymentRepository;
    private final CreatorPaymentRepository creatorPaymentRepository;
    private final IncidentReportRepository incidentReportRepository;

    @Transactional(readOnly = true)
    public Analytics analytics(User caller, int days) {
        if (caller == null || caller.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Réservé aux administrateurs");
        }
        if (days != 7 && days != 30 && days != 90) days = 30;

        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusDays(days - 1);
        LocalDate prevStart = start.minusDays(days);
        Instant from = prevStart.atStartOfDay(ZONE).toInstant();

        var revenue = creatorPaymentRepository.findByStatusAndPaidAtGreaterThanEqual(CreatorPayment.Status.PAID, from);
        var users = userRepository.findByCreatedAtGreaterThanEqual(from);
        var tontines = tontineRepository.findByCreatedAtGreaterThanEqual(from);
        var cotis = paymentRepository.findByConfirmedAtGreaterThanEqual(from).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PAID || p.getStatus() == PaymentStatus.LATE).toList();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (TontineStatus s : TontineStatus.values()) byStatus.put(s.name(), tontineRepository.countByStatus(s));

        var recent = creatorPaymentRepository.findTop8ByStatusOrderByPaidAtDesc(CreatorPayment.Status.PAID).stream()
                .map(c -> new RecentRevenue(c.getId(), c.getUser().getFullName(), c.getPurpose().name(), c.getAmount(), c.getPaidAt()))
                .toList();

        return new Analytics(days,
                kpi(revenue, CreatorPayment::getPaidAt, CreatorPayment::getAmount, start, prevStart),
                kpi(users, User::getCreatedAt, u -> BigDecimal.ONE, start, prevStart),
                kpi(tontines, Tontine::getCreatedAt, t -> BigDecimal.ONE, start, prevStart),
                kpi(cotis, Payment::getConfirmedAt, Payment::getAmount, start, prevStart),
                userRepository.count(),
                paymentRepository.countByStatus(PaymentStatus.DECLARED),
                incidentReportRepository.countByStatus(IncidentStatus.OPEN),
                byStatus,
                series(revenue, CreatorPayment::getPaidAt, CreatorPayment::getAmount, start, today),
                series(users, User::getCreatedAt, u -> BigDecimal.ONE, start, today),
                series(tontines, Tontine::getCreatedAt, t -> BigDecimal.ONE, start, today),
                series(cotis, Payment::getConfirmedAt, Payment::getAmount, start, today),
                recent);
    }

    private static <T> Kpi kpi(List<T> items, Function<T, Instant> at, Function<T, BigDecimal> val,
                               LocalDate start, LocalDate prevStart) {
        BigDecimal cur = BigDecimal.ZERO, prev = BigDecimal.ZERO;
        for (T it : items) {
            Instant i = at.apply(it);
            if (i == null) continue;
            LocalDate d = LocalDate.ofInstant(i, ZONE);
            BigDecimal v = Optional.ofNullable(val.apply(it)).orElse(BigDecimal.ZERO);
            if (!d.isBefore(start)) cur = cur.add(v);
            else if (!d.isBefore(prevStart)) prev = prev.add(v);
        }
        return new Kpi(cur, prev);
    }

    private static <T> List<DayPoint> series(List<T> items, Function<T, Instant> at, Function<T, BigDecimal> val,
                                             LocalDate start, LocalDate end) {
        Map<LocalDate, BigDecimal> m = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) m.put(d, BigDecimal.ZERO);
        for (T it : items) {
            Instant i = at.apply(it);
            if (i == null) continue;
            LocalDate d = LocalDate.ofInstant(i, ZONE);
            if (m.containsKey(d)) m.merge(d, Optional.ofNullable(val.apply(it)).orElse(BigDecimal.ZERO), BigDecimal::add);
        }
        return m.entrySet().stream().map(e -> new DayPoint(e.getKey(), e.getValue())).toList();
    }
}
