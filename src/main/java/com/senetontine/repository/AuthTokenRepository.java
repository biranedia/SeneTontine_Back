package com.senetontine.repository;

import com.senetontine.model.AuthToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHash(String tokenHash);

    // Plus ancien en premier : sert a determiner quelle session revoquer quand l'utilisateur
    // depasse le nombre maximal de sessions actives autorisees (voir AuthService.issueToken).
    List<AuthToken> findByUserIdAndRevokedFalseOrderByCreatedAtAsc(Long userId);
}
