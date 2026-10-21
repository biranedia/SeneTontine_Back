package com.senetontine.service;

import com.senetontine.dto.SavingsDtos.*;
import com.senetontine.model.*;
import com.senetontine.repository.SavingsEntryRepository;
import com.senetontine.repository.SavingsGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

// Epargne individuelle : objectifs personnels, versements et retraits declares, rappels
// d'echeance. Gratuite, sans aucun argent dans l'app. Chaque objectif n'est visible que de
// son proprietaire.
@Service
@RequiredArgsConstructor
public class SavingsService {

    private final SavingsGoalRepository goalRepository;
    private final SavingsEntryRepository entryRepository;
    private final NotificationService notificationService;

    public List<GoalSummary> list(User user) {
        return goalRepository.findByUserIdOrderByIdDesc(user.getId()).stream().map(this::toSummary).toList();
    }

    @Transactional
    public GoalSummary create(User user, CreateGoalRequest req) {
        if ((req.periodicAmount() == null) != (req.cycleDays() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Indique à la fois le montant et la fréquence des versements, ou aucun des deux.");
        }
        SavingsGoal g = new SavingsGoal();
        g.setUser(user);
        g.setName(req.name().trim());
        if (req.emoji() != null && !req.emoji().isBlank()) g.setEmoji(req.emoji());
        g.setTargetAmount(req.targetAmount());
        g.setPeriodicAmount(req.periodicAmount());
        g.setCycleDays(req.cycleDays());
        if (req.cycleDays() != null) g.setNextDueDate(LocalDate.now().plusDays(req.cycleDays()));
        goalRepository.save(g);
        return toSummary(g);
    }

    public GoalDetail detail(User user, Long goalId) {
        SavingsGoal g = findOwned(user, goalId);
        List<EntryInfo> entries = entryRepository.findTop100ByGoalIdOrderByIdDesc(goalId).stream()
                .map(e -> new EntryInfo(e.getId(), e.getType().name(), e.getAmount(), e.getNote(), e.getCreatedAt()))
                .toList();
        return new GoalDetail(toSummary(g), entries);
    }

    @Transactional
    public GoalDetail addEntry(User user, Long goalId, EntryRequest req) {
        SavingsGoal g = findOwned(user, goalId);
        if (req.type() == SavingsEntryType.WITHDRAWAL && req.amount().compareTo(saved(goalId)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Tu ne peux pas retirer plus que ce que tu as épargné.");
        }
        SavingsEntry e = new SavingsEntry();
        e.setGoal(g);
        e.setType(req.type());
        e.setAmount(req.amount());
        e.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        entryRepository.save(e);

        // Un versement repousse la prochaine echeance ; l'objectif est marque atteint (ou
        // de nouveau en cours apres un retrait).
        if (req.type() == SavingsEntryType.DEPOSIT && g.getCycleDays() != null) {
            LocalDate base = g.getNextDueDate() != null && g.getNextDueDate().isAfter(LocalDate.now())
                    ? g.getNextDueDate() : LocalDate.now();
            g.setNextDueDate(base.plusDays(g.getCycleDays()));
        }
        boolean reached = saved(goalId).compareTo(g.getTargetAmount()) >= 0;
        if (reached && g.getCompletedAt() == null) {
            g.setCompletedAt(Instant.now());
            notificationService.notify(user, NotificationType.SAVINGS_GOAL_REACHED,
                    "Objectif atteint 🎉", "Bravo ! Ton objectif \"" + g.getName() + "\" est atteint.");
        } else if (!reached) {
            g.setCompletedAt(null);
        }
        goalRepository.save(g);
        return detail(user, goalId);
    }

    @Transactional
    public void delete(User user, Long goalId) {
        goalRepository.delete(findOwned(user, goalId));
    }

    // Rappel la veille de chaque echeance, 8h05 (decale du rappel des tontines a 8h00).
    @Scheduled(cron = "0 5 8 * * *")
    @Transactional
    public void sendDueReminders() {
        for (SavingsGoal g : goalRepository.findByNextDueDateAndCompletedAtIsNull(LocalDate.now().plusDays(1))) {
            notificationService.notify(g.getUser(), NotificationType.SAVINGS_REMINDER,
                    "Épargne : versement demain ⏰",
                    "Pense à mettre de côté " + fcfa(g.getPeriodicAmount()) + " pour \"" + g.getName() + "\".");
        }
    }

    private SavingsGoal findOwned(User user, Long goalId) {
        return goalRepository.findById(goalId)
                .filter(g -> g.getUser().getId().equals(user.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Objectif introuvable"));
    }

    private BigDecimal saved(Long goalId) {
        return entryRepository.sumByGoalAndType(goalId, SavingsEntryType.DEPOSIT)
                .subtract(entryRepository.sumByGoalAndType(goalId, SavingsEntryType.WITHDRAWAL));
    }

    private GoalSummary toSummary(SavingsGoal g) {
        BigDecimal saved = g.getId() == null ? BigDecimal.ZERO : saved(g.getId());
        double progress = g.getTargetAmount().signum() == 0 ? 0
                : saved.divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP).doubleValue();
        return new GoalSummary(g.getId(), g.getName(), g.getEmoji(), g.getTargetAmount(), saved,
                Math.min(1, Math.max(0, progress)), g.getPeriodicAmount(), g.getCycleDays(), g.getNextDueDate(),
                g.getCompletedAt() != null);
    }

    private static String fcfa(BigDecimal amount) {
        if (amount == null) return "ton versement";
        return String.format(java.util.Locale.US, "%,d", amount.longValue()).replace(",", " ") + " FCFA";
    }
}
