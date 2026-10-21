package com.senetontine.service;

import com.senetontine.dto.PaymentDtos.DeclareRequest;
import com.senetontine.model.*;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.PotPayoutRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import com.senetontine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Flux sans argent : le membre declare son envoi Wave/OM, le createur confirme ; quand tout le
// tour est confirme, un versement de pot est ouvert pour le beneficiaire et le tour avance.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private TontineRepository tontineRepository;
    @Mock private TontineMemberRepository memberRepository;
    @Mock private PotPayoutRepository potPayoutRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;

    private PaymentService paymentService;
    private Tontine tontine;
    private User creator;
    private User member;
    private TontineMember creatorMembership;
    private TontineMember memberMembership;
    private final List<Payment> savedPayments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, tontineRepository, memberRepository,
                potPayoutRepository, notificationService, userRepository);

        tontine = new Tontine();
        tontine.setId(10L);
        tontine.setName("Famille");
        tontine.setStatus(TontineStatus.ACTIVE);
        tontine.setCurrentRound(1);
        tontine.setMembersRequired(2);
        tontine.setCotisationAmount(new BigDecimal("50000"));
        tontine.setPenaltyPerDay(new BigDecimal("1000"));
        tontine.setCycleDurationDays(30);
        tontine.setNextTurnDate(LocalDate.now().plusDays(5));

        creator = new User(); creator.setId(2L); creator.setFullName("Awa Créatrice");
        member = new User(); member.setId(1L); member.setFullName("Moussa Membre");

        // Le membre recoit le pot au tour 1, la creatrice au tour 2.
        memberMembership = new TontineMember();
        memberMembership.setTontine(tontine); memberMembership.setUser(member);
        memberMembership.setRole(MemberRole.MEMBER); memberMembership.setTurnOrder(1);
        creatorMembership = new TontineMember();
        creatorMembership.setTontine(tontine); creatorMembership.setUser(creator);
        creatorMembership.setRole(MemberRole.CREATOR); creatorMembership.setTurnOrder(2);

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(memberMembership));
        when(memberRepository.findByTontineIdAndUserId(10L, 2L)).thenReturn(Optional.of(creatorMembership));
        when(memberRepository.findByTontineId(10L)).thenReturn(List.of(memberMembership, creatorMembership));
        when(paymentRepository.save(any())).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            if (!savedPayments.contains(p)) savedPayments.add(p);
            return p;
        });
        when(paymentRepository.findByTontineIdOrderByRoundNumberAsc(10L)).thenAnswer(inv -> savedPayments);
        when(potPayoutRepository.findByTontineIdAndRoundNumber(anyLong(), anyInt())).thenReturn(Optional.empty());
    }

    @Test
    void declare_onTime_marksDeclaredAndNotifiesCreator() {
        var info = paymentService.declare(10L, member, new DeclareRequest(PaymentMethod.WAVE, "TX123"));

        assertThat(info.status()).isEqualTo("DECLARED");
        assertThat(info.reference()).isEqualTo("TX123");
        assertThat(info.penaltyAmount()).isEqualByComparingTo("0");
        verify(notificationService).notify(eq(creator), eq(NotificationType.PAYMENT_DECLARED), anyString(), anyString());
    }

    @Test
    void declare_late_computesIndicativePenalty() {
        tontine.setNextTurnDate(LocalDate.now().minusDays(3));

        var info = paymentService.declare(10L, member, new DeclareRequest(PaymentMethod.ORANGE_MONEY, null));

        assertThat(info.penaltyAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void declare_onCancelledTontine_isRejected() {
        tontine.setStatus(TontineStatus.CANCELLED);

        assertThatThrownBy(() -> paymentService.declare(10L, member, new DeclareRequest(PaymentMethod.WAVE, null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(paymentRepository, never()).save(any());
    }

    @Test
    void declare_twice_isRejected() {
        paymentService.declare(10L, member, new DeclareRequest(PaymentMethod.WAVE, null));
        when(paymentRepository.findByTontineIdAndUserIdAndRoundNumber(10L, 1L, 1))
                .thenReturn(Optional.of(savedPayments.get(0)));

        assertThatThrownBy(() -> paymentService.declare(10L, member, new DeclareRequest(PaymentMethod.WAVE, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void declare_byCreator_isAutoConfirmed() {
        var info = paymentService.declare(10L, creator, new DeclareRequest(PaymentMethod.WAVE, null));

        assertThat(info.status()).isEqualTo("PAID");
        verify(notificationService, never()).notify(eq(creator), eq(NotificationType.PAYMENT_DECLARED), anyString(), anyString());
    }

    @Test
    void confirm_rejectsNonCreator() {
        Payment p = declared(member, 500L);
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> paymentService.confirm(10L, 500L, member))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(p.getStatus()).isEqualTo(PaymentStatus.DECLARED);
    }

    @Test
    void confirm_lastPaymentOfRound_opensPotForBeneficiaryAndAdvancesRound() {
        Payment creatorPaid = declared(creator, 499L);
        creatorPaid.setStatus(PaymentStatus.PAID);
        Payment p = declared(member, 500L);
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(p));
        when(paymentRepository.countByUserIdAndStatus(1L, PaymentStatus.PAID)).thenReturn(1L);

        paymentService.confirm(10L, 500L, creator);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(p.getConfirmedAt()).isNotNull();
        ArgumentCaptor<PotPayout> payout = ArgumentCaptor.forClass(PotPayout.class);
        verify(potPayoutRepository).save(payout.capture());
        assertThat(payout.getValue().getBeneficiary()).isEqualTo(member);
        assertThat(payout.getValue().getAmount()).isEqualByComparingTo("100000");
        assertThat(payout.getValue().getSentAt()).isNull();
        verify(notificationService).notify(eq(creator), eq(NotificationType.POT_TO_SEND), anyString(), anyString());
        assertThat(tontine.getCurrentRound()).isEqualTo(2);
        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.ACTIVE);
    }

    @Test
    void confirm_lastRound_completesTontine() {
        tontine.setCurrentRound(2);
        Payment creatorPaid = declared(creator, 499L);
        creatorPaid.setRoundNumber(2);
        creatorPaid.setStatus(PaymentStatus.PAID);
        Payment p = declared(member, 500L);
        p.setRoundNumber(2);
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(p));

        paymentService.confirm(10L, 500L, creator);

        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.COMPLETED);
    }

    @Test
    void reject_resetsPaymentToDueAndNotifiesMember() {
        Payment p = declared(member, 500L);
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(p));

        paymentService.reject(10L, 500L, creator);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.DUE);
        assertThat(p.getDeclaredAt()).isNull();
        verify(notificationService).notify(eq(member), eq(NotificationType.PAYMENT_REJECTED), anyString(), anyString());
    }

    @Test
    void confirmLate_lowersTrustScoreButNeverBelowFloor() {
        Payment p = declared(member, 500L);
        p.setPenaltyAmount(new BigDecimal("2000"));
        when(paymentRepository.findById(500L)).thenReturn(Optional.of(p));
        when(paymentRepository.countByUserIdAndStatus(1L, PaymentStatus.PAID)).thenReturn(1L);
        when(paymentRepository.countByUserIdAndStatus(1L, PaymentStatus.LATE)).thenReturn(8L);

        paymentService.confirm(10L, 500L, creator);

        assertThat(p.getStatus()).isEqualTo(PaymentStatus.LATE);
        assertThat(member.getTrustScore()).isEqualByComparingTo("1.0");
    }

    @Test
    void markPotReceived_onlyByBeneficiary() {
        PotPayout payout = new PotPayout();
        payout.setTontine(tontine);
        payout.setRoundNumber(1);
        payout.setBeneficiary(member);
        payout.setAmount(new BigDecimal("100000"));
        when(potPayoutRepository.findByTontineIdAndRoundNumber(10L, 1)).thenReturn(Optional.of(payout));

        assertThatThrownBy(() -> paymentService.markPotReceived(10L, 1, creator))
                .isInstanceOf(ResponseStatusException.class);

        paymentService.markPotReceived(10L, 1, member);
        assertThat(payout.getReceivedAt()).isNotNull();
    }

    private Payment declared(User user, Long id) {
        Payment p = new Payment();
        p.setId(id);
        p.setTontine(tontine);
        p.setUser(user);
        p.setRoundNumber(1);
        p.setAmount(new BigDecimal("50000"));
        p.setPenaltyAmount(BigDecimal.ZERO);
        p.setMethod(PaymentMethod.WAVE);
        p.setStatus(PaymentStatus.DECLARED);
        p.setDeclaredAt(java.time.Instant.now());
        savedPayments.add(p);
        return p;
    }
}
