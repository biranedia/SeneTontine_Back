package com.senetontine.config;

import com.senetontine.model.UserRole;
import com.senetontine.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Promeut en ADMIN les comptes dont le telephone figure dans ADMIN_PHONES (separes par des
// virgules), au demarrage. Le compte doit deja exister (inscription normale dans l'app).
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;

    @Value("${app.admin-phones:}")
    private String adminPhones;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminPhones == null || adminPhones.isBlank()) return;
        for (String raw : adminPhones.split(",")) {
            String phone = raw.trim();
            if (phone.isEmpty()) continue;
            userRepository.findByPhone(phone).ifPresentOrElse(u -> {
                if (u.getRole() != UserRole.ADMIN) {
                    u.setRole(UserRole.ADMIN);
                    userRepository.save(u);
                    log.info("Compte {} promu ADMIN", phone);
                }
            }, () -> log.warn("ADMIN_PHONES : aucun compte pour {}", phone));
        }
    }
}
