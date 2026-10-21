package com.senetontine.repository;

import com.senetontine.model.AppNotification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppNotificationRepository extends JpaRepository<AppNotification, Long> {
    // Plafonne comme le reste des listes exposees a l'utilisateur (voir chat / retraits
    // en attente) : pas de liste illimitee en memoire meme pour un utilisateur tres actif.
    List<AppNotification> findTop50ByUserIdOrderByIdDesc(Long userId);

    long countByUserIdAndReadFalse(Long userId);
}
