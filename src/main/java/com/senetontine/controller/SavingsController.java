package com.senetontine.controller;

import com.senetontine.dto.SavingsDtos.*;
import com.senetontine.model.User;
import com.senetontine.service.SavingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/savings")
@RequiredArgsConstructor
public class SavingsController {

    private final SavingsService savingsService;

    @GetMapping
    public List<GoalSummary> list(@AuthenticationPrincipal User user) {
        return savingsService.list(user);
    }

    @PostMapping
    public GoalSummary create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateGoalRequest req) {
        return savingsService.create(user, req);
    }

    @GetMapping("/{id}")
    public GoalDetail detail(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return savingsService.detail(user, id);
    }

    @PostMapping("/{id}/entries")
    public GoalDetail addEntry(@AuthenticationPrincipal User user, @PathVariable Long id, @Valid @RequestBody EntryRequest req) {
        return savingsService.addEntry(user, id, req);
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal User user, @PathVariable Long id) {
        savingsService.delete(user, id);
    }
}
