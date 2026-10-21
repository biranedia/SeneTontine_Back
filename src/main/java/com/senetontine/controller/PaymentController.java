package com.senetontine.controller;

import com.senetontine.dto.PaymentDtos.DeclareRequest;
import com.senetontine.dto.PaymentDtos.PaymentInfo;
import com.senetontine.dto.PaymentDtos.PotPayoutInfo;
import com.senetontine.model.User;
import com.senetontine.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tontines/{tontineId}")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payments")
    public List<PaymentInfo> history(@AuthenticationPrincipal User user, @PathVariable Long tontineId) {
        return paymentService.history(tontineId, user);
    }

    // Le membre declare avoir envoye sa cotisation au createur (Wave / Orange Money).
    @PostMapping("/payments/declare")
    public PaymentInfo declare(@AuthenticationPrincipal User user, @PathVariable Long tontineId,
                               @Valid @RequestBody DeclareRequest req) {
        return paymentService.declare(tontineId, user, req);
    }

    @PostMapping("/payments/{paymentId}/confirm")
    public PaymentInfo confirm(@AuthenticationPrincipal User user, @PathVariable Long tontineId, @PathVariable Long paymentId) {
        return paymentService.confirm(tontineId, paymentId, user);
    }

    @PostMapping("/payments/{paymentId}/reject")
    public PaymentInfo reject(@AuthenticationPrincipal User user, @PathVariable Long tontineId, @PathVariable Long paymentId) {
        return paymentService.reject(tontineId, paymentId, user);
    }

    @GetMapping("/payouts")
    public List<PotPayoutInfo> payouts(@AuthenticationPrincipal User user, @PathVariable Long tontineId) {
        return paymentService.payouts(tontineId, user);
    }

    @PostMapping("/payouts/{round}/sent")
    public PotPayoutInfo potSent(@AuthenticationPrincipal User user, @PathVariable Long tontineId, @PathVariable int round) {
        return paymentService.markPotSent(tontineId, round, user);
    }

    @PostMapping("/payouts/{round}/received")
    public PotPayoutInfo potReceived(@AuthenticationPrincipal User user, @PathVariable Long tontineId, @PathVariable int round) {
        return paymentService.markPotReceived(tontineId, round, user);
    }
}
