package com.senetontine.service;

import com.senetontine.model.NotificationType;
import com.senetontine.model.PaymentStatus;
import com.senetontine.model.Tontine;
import com.senetontine.model.TontineMember;
import com.senetontine.model.TontineStatus;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

// Rappel proactif de cotisation, AVANT l'echeance plutot qu'apres : jusqu'ici un membre
// n'apprenait qu'il etait en retard qu'une fois la penalite deja appliquee (voir
// PaymentService.pay), rien ne le prevenait a l'avance que le tour approchait. Tourne une fois
// par jour ; envoie un rappel unique par tour, REMINDER_LEAD_DAYS avant l'echeance, uniquement
// aux membres qui n'ont pas encore paye leur cotisation du tour en cours.
@Component
@RequiredArgsConstructor
public class PaymentReminderService {

    // Delai d'anticipation du rappel. Une seule relance avant l'echeance suffit pour l'instant -
    // suffisamment tot pour laisser le temps de reunir l'argent, pas si tot que le rappel soit
    // oublie d'ici l'echeance reelle.
    private static final int REMINDER_LEAD_DAYS = 3;

    private final TontineRepository tontineRepository;
    private final TontineMemberRepository memberRepository;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    // 8h du matin : assez tot pour laisser la journee au membre pour cotiser, pas au milieu de
    // la nuit ou une notification serait juste ignoree/oubliee au reveil.
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendUpcomingDueReminders() {
        LocalDate targetDate = LocalDate.now().plusDays(REMINDER_LEAD_DAYS);
        for (Tontine tontine : tontineRepository.findByStatusAndNextTurnDate(TontineStatus.ACTIVE, targetDate)) {
            // Deja envoye pour ce tour (job relance le meme jour, ou tour pas encore avance
            // depuis) : on ne relance pas une seconde fois pour le meme tour.
            if (tontine.getReminderSentRound() != null && tontine.getReminderSentRound().equals(tontine.getCurrentRound())) {
                continue;
            }

            for (TontineMember member : memberRepository.findByTontineId(tontine.getId())) {
                boolean alreadyPaid = paymentRepository
                        .findByTontineIdAndUserIdAndRoundNumber(tontine.getId(), member.getUser().getId(), tontine.getCurrentRound())
                        .map(p -> p.getStatus() != PaymentStatus.DUE)
                        .orElse(false);
                if (alreadyPaid) continue;

                notificationService.notify(member.getUser(), NotificationType.PAYMENT_REMINDER,
                        "Cotisation bientôt due ⏰",
                        "Ta cotisation pour le tour " + tontine.getCurrentRound() + " de \"" + tontine.getName()
                                + "\" est due dans " + REMINDER_LEAD_DAYS + " jours. Envoie-la au créateur par Wave ou Orange Money, puis déclare ton paiement dans l'app.");
            }

            tontine.setReminderSentRound(tontine.getCurrentRound());
            tontineRepository.save(tontine);
        }
    }
}
