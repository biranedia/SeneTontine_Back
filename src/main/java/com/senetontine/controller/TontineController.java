package com.senetontine.controller;

import com.senetontine.dto.TontineDtos.AddMemberByPhoneRequest;
import com.senetontine.dto.TontineDtos.CreateTontineRequest;
import com.senetontine.dto.TontineDtos.JoinByCodeRequest;
import com.senetontine.dto.TontineDtos.LeaveTontineResponse;
import com.senetontine.dto.TontineDtos.TontineDetail;
import com.senetontine.dto.TontineDtos.TontineSummary;
import com.senetontine.model.User;
import com.senetontine.service.TontineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tontines")
@RequiredArgsConstructor
public class TontineController {

    private final TontineService tontineService;

    @GetMapping
    public List<TontineSummary> mine(@AuthenticationPrincipal User user) {
        return tontineService.listForUser(user.getId());
    }

    // Chemin litteral "/discover" avant "/{id}" : Spring priorise correctement les segments
    // litteraux sur les variables de chemin, pas d'ambiguite de routage ici.
    @GetMapping("/discover")
    public List<TontineSummary> discover(@AuthenticationPrincipal User user) {
        return tontineService.discover(user);
    }

    @GetMapping("/{id}")
    public TontineDetail detail(@AuthenticationPrincipal User caller, @PathVariable Long id) {
        return tontineService.getDetail(id, caller);
    }

    @PostMapping
    public TontineSummary create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateTontineRequest req) {
        return tontineService.create(user, req);
    }

    @PostMapping("/{id}/join")
    public TontineSummary join(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return tontineService.join(id, user);
    }

    // Rejoindre via le code court partage par le createur (ex: via WhatsApp) plutot que l'ID
    // technique de la tontine.
    @PostMapping("/join")
    public TontineSummary joinByCode(@AuthenticationPrincipal User user, @Valid @RequestBody JoinByCodeRequest req) {
        return tontineService.joinByCode(req.inviteCode(), user);
    }

    // Reserve au createur (ou un admin) et uniquement tant que la tontine est PENDING (voir
    // TontineService.updateSettings).
    @PutMapping("/{id}")
    public TontineDetail update(@AuthenticationPrincipal User caller, @PathVariable Long id,
                                 @Valid @RequestBody CreateTontineRequest req) {
        return tontineService.updateSettings(id, caller, req);
    }

    // Renouveler une tontine terminee (createur, paye via NabooPay : voir BillingService).
    @PostMapping("/{id}/renew")
    public TontineSummary renew(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return tontineService.renew(id, user);
    }

    @PostMapping("/{id}/leave")
    public LeaveTontineResponse leave(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return tontineService.leave(id, user);
    }

    // Le createur (ou un admin) ajoute directement un membre deja inscrit, par son numero.
    @PostMapping("/{id}/members")
    public TontineSummary addMemberByPhone(@AuthenticationPrincipal User caller, @PathVariable Long id,
                                            @Valid @RequestBody AddMemberByPhoneRequest req) {
        return tontineService.addMemberByPhone(id, req.phone(), caller);
    }

}
