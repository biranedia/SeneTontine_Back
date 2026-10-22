package com.senetontine.service;

import com.senetontine.dto.TontineDtos.CreateTontineRequest;
import com.senetontine.model.*;
import com.senetontine.repository.PaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import com.senetontine.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TontineServiceTest {

    @Mock private TontineRepository tontineRepository;
    @Mock private TontineMemberRepository memberRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private NotificationService notificationService;
    @Mock private UserRepository userRepository;
    @Mock private BillingService billingService;
    @Mock private com.senetontine.repository.PotPayoutRepository potPayoutRepository;

    private TontineService tontineService;
    private Tontine tontine;
    private User user;

    @BeforeEach
    void setUp() {
        tontineService = new TontineService(tontineRepository, memberRepository, paymentRepository,
                notificationService, userRepository, billingService, potPayoutRepository);

        tontine = new Tontine();
        tontine.setId(10L);
        tontine.setName("Tontine Test");
        tontine.setMembersRequired(3);
        tontine.setCycleDurationDays(30);
        tontine.setCotisationAmount(new BigDecimal("50000"));
        tontine.setVisibility(TontineVisibility.PUBLIC);
        tontine.setStatus(TontineStatus.PENDING);
        tontine.setCurrentRound(1);

        user = new User();
        user.setId(1L);
        user.setFullName("Nouveau Membre");

        when(tontineRepository.findById(10L)).thenReturn(Optional.of(tontine));
        when(memberRepository.findByTontineId(10L)).thenReturn(List.of());
    }

    private CreateTontineRequest request(String wave, String om) {
        return new CreateTontineRequest("Famille", new BigDecimal("25000"), 4, null, null, false, 30, wave, om, null);
    }

    private TontineMember membership(MemberRole role, Integer turnOrder) {
        TontineMember m = new TontineMember();
        m.setTontine(tontine);
        m.setUser(user);
        m.setRole(role);
        m.setTurnOrder(turnOrder);
        return m;
    }

    @Test
    void create_requiresAtLeastOnePayoutNumber() {
        assertThatThrownBy(() -> tontineService.create(user, request(null, " ")))
                .isInstanceOf(ResponseStatusException.class);
        verify(tontineRepository, never()).save(any());
    }

    @Test
    void create_storesPayoutNumbersAndCreatorAsTurnOne() {
        when(tontineRepository.existsByInviteCode(anyString())).thenReturn(false);

        tontineService.create(user, request("77 123 45 67", null));

        verify(tontineRepository).save(argThat(t -> "77 123 45 67".equals(t.getWavePhone()) && t.getOrangeMoneyPhone() == null));
        verify(memberRepository).save(argThat(m -> m.getRole() == MemberRole.CREATOR && m.getTurnOrder() == 1));
    }

    @Test
    void create_blockedWithoutPaidRight() {
        doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.PAYMENT_REQUIRED))
                .when(billingService).consumeCreationRight(user);

        assertThatThrownBy(() -> tontineService.create(user, request("771234567", null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(tontineRepository, never()).save(any());
    }

    @Test
    void join_rejectsPrivateTontine() {
        tontine.setVisibility(TontineVisibility.PRIVATE);
        assertThatThrownBy(() -> tontineService.join(10L, user)).isInstanceOf(ResponseStatusException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void join_rejectsCancelledOrStartedTontine() {
        tontine.setStatus(TontineStatus.CANCELLED);
        assertThatThrownBy(() -> tontineService.join(10L, user)).isInstanceOf(ResponseStatusException.class);
        tontine.setStatus(TontineStatus.ACTIVE);
        assertThatThrownBy(() -> tontineService.join(10L, user)).isInstanceOf(ResponseStatusException.class);
        verify(memberRepository, never()).save(any());
    }

    @Test
    void join_rejectsAlreadyMember() {
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(new TontineMember()));
        assertThatThrownBy(() -> tontineService.join(10L, user)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void join_activatesTontineWhenLastRequiredMemberJoins() {
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.empty());
        when(memberRepository.countByTontineId(10L)).thenReturn(2L);

        tontineService.join(10L, user);

        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.ACTIVE);
        assertThat(tontine.getNextTurnDate()).isNotNull();
        verify(tontineRepository).save(tontine);
    }

    @Test
    void leave_rejectsCreator() {
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership(MemberRole.CREATOR, 1)));
        assertThatThrownBy(() -> tontineService.leave(10L, user)).isInstanceOf(ResponseStatusException.class);
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void leave_rejectsMemberWhoAlreadyReceivedTheirTurn() {
        tontine.setStatus(TontineStatus.ACTIVE);
        tontine.setCurrentRound(3);
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership(MemberRole.MEMBER, 2)));

        assertThatThrownBy(() -> tontineService.leave(10L, user)).isInstanceOf(ResponseStatusException.class);
        verify(memberRepository, never()).delete(any());
    }

    @Test
    void leave_afterDeclaringThisRound_flagsSettlementWithCreator() {
        tontine.setStatus(TontineStatus.ACTIVE);
        TontineMember m = membership(MemberRole.MEMBER, 2);
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(m));
        Payment p = new Payment();
        p.setAmount(new BigDecimal("50000"));
        p.setPenaltyAmount(BigDecimal.ZERO);
        p.setStatus(PaymentStatus.DECLARED);
        when(paymentRepository.findByTontineIdAndUserIdAndRoundNumber(10L, 1L, 1)).thenReturn(Optional.of(p));

        // Apres le depart, il reste 2 membres : la tontine continue avec 2 tours.
        TontineMember a = membership(MemberRole.CREATOR, 1);
        TontineMember b = membership(MemberRole.MEMBER, 3);
        when(memberRepository.findByTontineId(10L)).thenReturn(List.of(a, b));

        var result = tontineService.leave(10L, user);

        assertThat(result.settleWithCreator()).isTrue();
        assertThat(result.amountPaidThisRound()).isEqualByComparingTo("50000");
        verify(paymentRepository).delete(p);
        verify(memberRepository).delete(m);
        assertThat(tontine.getMembersRequired()).isEqualTo(2);
        assertThat(b.getTurnOrder()).isEqualTo(2);
        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.ACTIVE);
    }

    @Test
    void leave_activeTontineWithOneMemberLeft_isCancelled() {
        tontine.setStatus(TontineStatus.ACTIVE);
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership(MemberRole.MEMBER, 2)));
        when(memberRepository.findByTontineId(10L)).thenReturn(List.of(membership(MemberRole.CREATOR, 1)));

        tontineService.leave(10L, user);

        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.CANCELLED);
    }

    @Test
    void leave_pendingTontine_keepsGroupSize() {
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership(MemberRole.MEMBER, 2)));

        tontineService.leave(10L, user);

        assertThat(tontine.getMembersRequired()).isEqualTo(3);
        assertThat(tontine.getStatus()).isEqualTo(TontineStatus.PENDING);
    }

    @Test
    void updateSettings_rejectedOnceStarted() {
        tontine.setStatus(TontineStatus.ACTIVE);
        when(memberRepository.findByTontineIdAndUserId(10L, 1L)).thenReturn(Optional.of(membership(MemberRole.CREATOR, 1)));

        assertThatThrownBy(() -> tontineService.updateSettings(10L, user, request("771234567", null)))
                .isInstanceOf(ResponseStatusException.class);
    }
}
