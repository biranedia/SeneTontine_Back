package com.senetontine.service;

import com.senetontine.dto.SavingsDtos.CreateGoalRequest;
import com.senetontine.dto.SavingsDtos.EntryRequest;
import com.senetontine.model.*;
import com.senetontine.repository.SavingsEntryRepository;
import com.senetontine.repository.SavingsGoalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SavingsServiceTest {

    @Mock private SavingsGoalRepository goalRepository;
    @Mock private SavingsEntryRepository entryRepository;
    @Mock private NotificationService notificationService;

    private SavingsService service;
    private User owner;
    private User stranger;
    private SavingsGoal goal;

    @BeforeEach
    void setUp() {
        service = new SavingsService(goalRepository, entryRepository, notificationService);
        owner = new User(); owner.setId(1L);
        stranger = new User(); stranger.setId(2L);
        goal = new SavingsGoal();
        goal.setId(7L);
        goal.setUser(owner);
        goal.setName("Tabaski");
        goal.setTargetAmount(new BigDecimal("100000"));
        when(goalRepository.findById(7L)).thenReturn(Optional.of(goal));
        when(entryRepository.sumByGoalAndType(eq(7L), any())).thenReturn(BigDecimal.ZERO);
    }

    @Test
    void create_requiresAmountAndFrequencyTogether() {
        assertThatThrownBy(() -> service.create(owner,
                new CreateGoalRequest("Tabaski", null, new BigDecimal("100000"), new BigDecimal("10000"), null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void goal_isPrivateToItsOwner() {
        assertThatThrownBy(() -> service.detail(stranger, 7L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void withdrawal_cannotExceedSavedAmount() {
        when(entryRepository.sumByGoalAndType(7L, SavingsEntryType.DEPOSIT)).thenReturn(new BigDecimal("5000"));
        assertThatThrownBy(() -> service.addEntry(owner, 7L,
                new EntryRequest(SavingsEntryType.WITHDRAWAL, new BigDecimal("6000"), null)))
                .isInstanceOf(ResponseStatusException.class);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void reachingTarget_marksGoalCompletedAndNotifies() {
        when(entryRepository.sumByGoalAndType(7L, SavingsEntryType.DEPOSIT)).thenReturn(new BigDecimal("100000"));

        service.addEntry(owner, 7L, new EntryRequest(SavingsEntryType.DEPOSIT, new BigDecimal("20000"), null));

        assertThat(goal.getCompletedAt()).isNotNull();
        verify(notificationService).notify(eq(owner), eq(NotificationType.SAVINGS_GOAL_REACHED), anyString(), anyString());
    }
}
