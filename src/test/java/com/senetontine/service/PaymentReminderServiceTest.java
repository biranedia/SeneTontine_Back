package com.senetontine.service;

import com.senetontine.model.*;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Rappel proactif de cotisation avant l'echeance (voir PaymentReminderService). Couvre surtout
// deux points sensibles : ne jamais relancer un membre qui a deja paye, et ne jamais envoyer
// deux fois le meme rappel pour un tour donne (idempotence via Tontine.reminderSentRound).
@ExtendWith(MockitoExtension.class)
class PaymentReminderServiceTest {

    @Mock private TontineRepository tontineRepository;
    @Mock private TontineMemberRepository memberRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private NotificationService notificationService;

    private PaymentReminderService reminderService;

    private Tontine tontine;
    private User unpaidMember;
    private User paidMember;

    @BeforeEach
    void setUp() {
        reminderService = new PaymentReminderService(tontineRepository, memberRepository, paymentRepository, notificationService);

        tontine = new Tontine();
        tontine.setId(10L);
        tontine.setName("Tontine Test");
        tontine.setCurrentRound(2);
        tontine.setStatus(TontineStatus.ACTIVE);

        unpaidMember = new User();
        unpaidMember.setId(1L);

        paidMember = new User();
        paidMember.setId(2L);
    }

    @Test
    void sendUpcomingDueReminders_notifiesOnlyMembersWhoHaveNotPaidCurrentRound() {
        when(tontineRepository.findByStatusAndNextTurnDate(eq(TontineStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(tontine));

        TontineMember m1 = new TontineMember();
        m1.setUser(unpaidMember);
        TontineMember m2 = new TontineMember();
        m2.setUser(paidMember);
        when(memberRepository.findByTontineId(10L)).thenReturn(List.of(m1, m2));

        when(paymentRepository.findByTontineIdAndUserIdAndRoundNumber(10L, 1L, 2)).thenReturn(Optional.empty());
        Payment paid = new Payment();
        paid.setStatus(PaymentStatus.PAID);
        when(paymentRepository.findByTontineIdAndUserIdAndRoundNumber(10L, 2L, 2)).thenReturn(Optional.of(paid));

        reminderService.sendUpcomingDueReminders();

        verify(notificationService).notify(eq(unpaidMember), eq(NotificationType.PAYMENT_REMINDER), anyString(), anyString());
        verify(notificationService, never()).notify(eq(paidMember), any(), anyString(), anyString());
        assertThat(tontine.getReminderSentRound()).isEqualTo(2);
        verify(tontineRepository).save(tontine);
    }

    @Test
    void sendUpcomingDueReminders_skipsTontineAlreadyRemindedForThisRound() {
        tontine.setReminderSentRound(2); // deja envoye pour le tour en cours
        when(tontineRepository.findByStatusAndNextTurnDate(eq(TontineStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(tontine));

        reminderService.sendUpcomingDueReminders();

        verifyNoInteractions(memberRepository, notificationService);
        verify(tontineRepository, never()).save(any());
    }

    @Test
    void sendUpcomingDueReminders_sendsAgainOnceRoundHasAdvanced() {
        tontine.setReminderSentRound(1); // rappel envoye pour le tour PRECEDENT, tour actuel = 2
        when(tontineRepository.findByStatusAndNextTurnDate(eq(TontineStatus.ACTIVE), any(LocalDate.class)))
                .thenReturn(List.of(tontine));

        TontineMember m1 = new TontineMember();
        m1.setUser(unpaidMember);
        when(memberRepository.findByTontineId(10L)).thenReturn(List.of(m1));
        when(paymentRepository.findByTontineIdAndUserIdAndRoundNumber(10L, 1L, 2)).thenReturn(Optional.empty());

        reminderService.sendUpcomingDueReminders();

        verify(notificationService).notify(eq(unpaidMember), eq(NotificationType.PAYMENT_REMINDER), anyString(), anyString());
        assertThat(tontine.getReminderSentRound()).isEqualTo(2);
    }
}
