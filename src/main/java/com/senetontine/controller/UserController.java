package com.senetontine.controller;

import com.senetontine.dto.UserDtos.DeleteAccountRequest;
import com.senetontine.dto.UserDtos.ProfileInfo;
import com.senetontine.dto.UserDtos.UpdateAvatarRequest;
import com.senetontine.dto.UserDtos.UpdateNameRequest;
import com.senetontine.model.User;
import com.senetontine.repository.UserRepository;
import com.senetontine.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final AccountService accountService;

    @GetMapping
    public ProfileInfo me(@AuthenticationPrincipal User user) {
        return toInfo(user);
    }

    @PutMapping("/avatar")
    public ProfileInfo updateAvatar(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateAvatarRequest req) {
        user.setAvatarBase64(req.avatarBase64());
        userRepository.save(user);
        return toInfo(user);
    }

    @PutMapping("/name")
    public ProfileInfo updateName(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateNameRequest req) {
        user.setFullName(req.fullName().trim());
        userRepository.save(user);
        return toInfo(user);
    }

    // Suppression de compte (exigence Google Play) : POST plutot que DELETE pour transporter
    // le mot de passe de confirmation dans le corps de facon fiable sur tous les clients.
    @PostMapping("/delete")
    public void deleteAccount(@AuthenticationPrincipal User user, @Valid @RequestBody DeleteAccountRequest req) {
        accountService.deleteAccount(user, req.password());
    }

    private ProfileInfo toInfo(User user) {
        return new ProfileInfo(user.getId(), user.getFullName(), user.getPhone(),
                user.getRole().name(), user.getTrustScore(), user.getAvatarBase64());
    }
}
