package com.senetontine.service;

import com.senetontine.dto.ActivityDtos.ActivityItem;
import com.senetontine.dto.ActivityDtos.UserStats;
import com.senetontine.model.Payment;
import com.senetontine.model.PaymentStatus;
import com.senetontine.model.PotPayout;
import com.senetontine.model.TontineStatus;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.PotPayoutRepository;
import com.senetontine.repository.TontineMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

// Historique personnel : cotisations confirmees (envoyees au createur) et pots recus,
// toutes tontines confondues. Calcule a la volee a partir de Payment et PotPayout.
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final PaymentRepository paymentRepository;
    private final PotPayoutRepository potPayoutRepository;
    private final TontineMemberRepository memberRepository;

    public List<ActivityItem> history(Long userId) {
        Stream<ActivityItem> contributions = Stream.concat(
                        paymentRepository.findByUserIdAndStatusOrderByPaidAtDesc(userId, PaymentStatus.PAID).stream(),
                        paymentRepository.findByUserIdAndStatusOrderByPaidAtDesc(userId, PaymentStatus.LATE).stream())
                .map(this::toContributionItem);

        Stream<ActivityItem> payouts = potPayoutRepository.findByBeneficiaryIdOrderByIdDesc(userId).stream()
                .filter(p -> p.getReceivedAt() != null || p.getSentAt() != null)
                .map(this::toPayoutItem);

        return Stream.concat(contributions, payouts)
                .sorted(Comparator.comparing(ActivityItem::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public UserStats stats(Long userId) {
        long activeTontines = memberRepository.countByUserIdAndTontine_Status(userId, TontineStatus.ACTIVE);
        List<Payment> confirmed = Stream.concat(
                paymentRepository.findByUserIdAndStatusOrderByPaidAtDesc(userId, PaymentStatus.PAID).stream(),
                paymentRepository.findByUserIdAndStatusOrderByPaidAtDesc(userId, PaymentStatus.LATE).stream()).toList();
        BigDecimal total = confirmed.stream()
                .map(p -> p.getAmount().add(p.getPenaltyAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new UserStats((int) activeTontines, total, confirmed.size());
    }

    private ActivityItem toContributionItem(Payment p) {
        return new ActivityItem("CONTRIBUTION", "Cotisation " + p.getTontine().getName(),
                p.getAmount().add(p.getPenaltyAmount()).negate(), p.getPaidAt());
    }

    private ActivityItem toPayoutItem(PotPayout p) {
        return new ActivityItem("PAYOUT", "Pot reçu · " + p.getTontine().getName(), p.getAmount(),
                p.getReceivedAt() != null ? p.getReceivedAt() : p.getSentAt());
    }
}
