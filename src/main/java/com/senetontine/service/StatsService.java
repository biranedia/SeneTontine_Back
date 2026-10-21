package com.senetontine.service;

import com.senetontine.dto.StatsDtos.TontineBreakdown;
import com.senetontine.dto.StatsDtos.UserStatsInfo;
import com.senetontine.dto.TontineDtos.TontineSummary;
import com.senetontine.model.Payment;
import com.senetontine.model.PaymentStatus;
import com.senetontine.model.TontineStatus;
import com.senetontine.model.User;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

// Ecran "Statistiques" cote app : avant, cet ecran affichait des donnees FICTIVES (le fichier
// data/mock.ts du prototype initial, jamais remplace par de vrais appels API lors du passage
// au vrai backend) - un utilisateur voyait toujours le meme solde/score peu importe son
// activite reelle. Ce service calcule les vrais chiffres a partir des paiements et tontines
// de l'utilisateur.
@Service
@RequiredArgsConstructor
public class StatsService {

    private final PaymentRepository paymentRepository;
    private final TontineMemberRepository memberRepository;
    private final TontineService tontineService;

    public UserStatsInfo getMyStats(User user) {
        List<Payment> paidPayments = paymentRepository.findByUserIdAndStatus(user.getId(), PaymentStatus.PAID);

        BigDecimal totalSaved = BigDecimal.ZERO;
        BigDecimal monthGain = BigDecimal.ZERO;
        YearMonth currentMonth = YearMonth.now(ZoneId.systemDefault());

        for (Payment p : paidPayments) {
            BigDecimal amount = p.getAmount().add(p.getPenaltyAmount());
            totalSaved = totalSaved.add(amount);
            if (p.getPaidAt() != null) {
                YearMonth paidMonth = YearMonth.from(p.getPaidAt().atZone(ZoneId.systemDefault()));
                if (paidMonth.equals(currentMonth)) {
                    monthGain = monthGain.add(amount);
                }
            }
        }

        long activeTontines = memberRepository.countByUserIdAndTontine_Status(user.getId(), TontineStatus.ACTIVE);
        long pendingTontines = memberRepository.countByUserIdAndTontine_Status(user.getId(), TontineStatus.PENDING);

        List<TontineSummary> mine = tontineService.listForUser(user.getId());
        List<TontineBreakdown> breakdown = mine.stream()
                .map(t -> new TontineBreakdown(t.id(), t.name(), t.emoji(), t.progress()))
                .toList();

        return new UserStatsInfo(
                totalSaved, monthGain, activeTontines, pendingTontines,
                (long) paidPayments.size(), user.getTrustScore(), breakdown
        );
    }
}
