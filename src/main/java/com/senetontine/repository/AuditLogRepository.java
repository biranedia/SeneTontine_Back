package com.senetontine.repository;

import com.senetontine.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    // Plafonne comme toutes les autres listes exposees a l'utilisateur (chat, notifications,
    // retraits en attente) : pas de liste illimitee en memoire. Les plus recents en premier,
    // c'est ce qu'un admin veut voir en priorite.
    List<AuditLog> findTop200ByOrderByIdDesc();
}
