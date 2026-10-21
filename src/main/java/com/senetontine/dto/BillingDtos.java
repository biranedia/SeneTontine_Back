package com.senetontine.dto;

import com.senetontine.model.CreatorPayment;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public class BillingDtos {

    // free : 1re tontine offerte ; hasCredit : un paiement deja effectue attend d'etre utilise.
    public record Quote(boolean free, boolean hasCredit, BigDecimal price) {}

    public record CheckoutRequest(@NotNull CreatorPayment.Purpose purpose, Long tontineId) {}

    public record CheckoutResponse(Long paymentId, String checkoutUrl) {}

    public record PaymentStatusInfo(Long paymentId, String purpose, Long tontineId, String status, BigDecimal amount) {}
}
