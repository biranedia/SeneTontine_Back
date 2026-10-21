package com.senetontine.service;

import com.senetontine.model.*;
import com.senetontine.model.CreatorPayment.Purpose;
import com.senetontine.model.CreatorPayment.Status;
import com.senetontine.repository.CreatorPaymentRepository;
import com.senetontine.repository.TontineMemberRepository;
import com.senetontine.repository.TontineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingServiceTest {

    @Mock private CreatorPaymentRepository paymentRepository;
    @Mock private TontineRepository tontineRepository;
    @Mock private TontineMemberRepository memberRepository;
    @Mock private NabooPayClient nabooPay;
    @Mock private NotificationService notificationService;

    private BillingService service;
    private User user;
    private CreatorPayment pending;

    @BeforeEach
    void setUp() {
        service = new BillingService(paymentRepository, tontineRepository, memberRepository, nabooPay, notificationService);
        ReflectionTestUtils.setField(service, "price", new BigDecimal("2500"));
        ReflectionTestUtils.setField(service, "publicUrl", "https://app.test");
        user = new User();
        user.setId(1L);
        user.setFullName("Awa Diop");
        user.setPhone("77 123 45 67");

        pending = new CreatorPayment();
        pending.setId(9L);
        pending.setUser(user);
        pending.setPurpose(Purpose.CREATE);
        pending.setAmount(new BigDecimal("2500"));
        pending.setOrderId("order_1");
        when(paymentRepository.lockByOrderId("order_1")).thenReturn(Optional.of(pending));
    }

    @Test
    void firstTontine_isFree() {
        when(tontineRepository.countByCreatedById(1L)).thenReturn(0L);
        service.consumeCreationRight(user);
        verify(paymentRepository, never()).lockAvailableCreationRights(anyLong());
    }

    @Test
    void secondTontine_withoutPayment_requiresPayment() {
        when(tontineRepository.countByCreatedById(1L)).thenReturn(1L);
        when(paymentRepository.lockAvailableCreationRights(1L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.consumeCreationRight(user)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void paidRight_isConsumedOnce() {
        when(tontineRepository.countByCreatedById(1L)).thenReturn(1L);
        pending.setStatus(Status.PAID);
        when(paymentRepository.lockAvailableCreationRights(1L)).thenReturn(List.of(pending));

        service.consumeCreationRight(user);

        assertThat(pending.getConsumedAt()).isNotNull();
    }

    @Test
    void webhookWithBadSignature_isRejectedWithoutTouchingPayments() {
        when(nabooPay.isValidSignature(anyString(), any())).thenReturn(false);
        assertThatThrownBy(() -> service.handleWebhook("{\"order_id\":\"order_1\"}", "faux"))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(paymentRepository);
    }

    @Test
    void paymentIsCreditedOnlyAfterProviderConfirmsFullAmount() throws Exception {
        when(nabooPay.getTransaction("order_1")).thenReturn(new NabooPayClient.Transaction("order_1", null, "paid", new BigDecimal("100")));
        service.verifyWithProvider("order_1");
        assertThat(pending.getStatus()).isEqualTo(Status.PENDING); // montant insuffisant : jamais credite

        when(nabooPay.getTransaction("order_1")).thenReturn(new NabooPayClient.Transaction("order_1", null, "paid", new BigDecimal("2500")));
        service.verifyWithProvider("order_1");
        assertThat(pending.getStatus()).isEqualTo(Status.PAID);
    }

    @Test
    void duplicateWebhook_doesNotReprocessAPaidPayment() throws Exception {
        pending.setStatus(Status.PAID);
        service.verifyWithProvider("order_1");
        verify(nabooPay, never()).getTransaction(any());
    }

    @Test
    void checkout_refusedWhenFreeOrAlreadyPaid() {
        when(tontineRepository.countByCreatedById(1L)).thenReturn(0L);
        assertThatThrownBy(() -> service.checkout(user, Purpose.CREATE, null)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void renewal_onlyForCompletedTontineOfItsCreator() {
        Tontine t = new Tontine();
        t.setId(5L);
        t.setStatus(TontineStatus.ACTIVE);
        when(tontineRepository.findById(5L)).thenReturn(Optional.of(t));
        TontineMember creator = new TontineMember();
        creator.setRole(MemberRole.CREATOR);
        when(memberRepository.findByTontineIdAndUserId(5L, 1L)).thenReturn(Optional.of(creator));

        assertThatThrownBy(() -> service.requireRenewable(user, 5L)).isInstanceOf(ResponseStatusException.class);
        t.setStatus(TontineStatus.COMPLETED);
        assertThat(service.requireRenewable(user, 5L)).isSameAs(t);
    }

    @Test
    void phone_isConvertedToInternationalFormat() {
        assertThat(BillingService.internationalPhone("77 123 45 67")).isEqualTo("+221771234567");
        assertThat(BillingService.internationalPhone("+221 77 123 45 67")).isEqualTo("+221771234567");
    }

    @Test
    void nabooPaySignature_isVerifiedWithHmacSha256() throws Exception {
        NabooPayClient client = new NabooPayClient("https://api.test", "key", "secret", JsonMapper.builder().build());
        String body = "{\"order_id\":\"order_1\",\"transaction_status\":\"paid\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertThat(client.isValidSignature(body, sig)).isTrue();
        assertThat(client.isValidSignature(body, "0".repeat(64))).isFalse();
        assertThat(client.isValidSignature(body.replace("paid", "xpaid"), sig)).isFalse();
    }
}
