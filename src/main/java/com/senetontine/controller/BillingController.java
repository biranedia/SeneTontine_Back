package com.senetontine.controller;

import com.senetontine.dto.BillingDtos.*;
import com.senetontine.model.CreatorPayment;
import com.senetontine.model.User;
import com.senetontine.service.BillingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/billing")
@RequiredArgsConstructor
public class BillingController {

    private final BillingService billingService;

    @GetMapping("/quote")
    public Quote quote(@AuthenticationPrincipal User user, @RequestParam CreatorPayment.Purpose purpose,
                       @RequestParam(required = false) Long tontineId) {
        return billingService.quote(user, purpose, tontineId);
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@AuthenticationPrincipal User user, @Valid @RequestBody CheckoutRequest req) {
        return billingService.checkout(user, req.purpose(), req.tontineId());
    }

    @PostMapping("/payments/{id}/refresh")
    public PaymentStatusInfo refresh(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return billingService.refresh(user, id);
    }

    // Public (appele par NabooPay) : protege par la signature HMAC, verifiee dans le service.
    @PostMapping("/webhook/naboopay")
    public void webhook(@RequestBody String rawBody, @RequestHeader(value = "X-Signature", required = false) String signature) {
        billingService.handleWebhook(rawBody, signature);
    }
}
