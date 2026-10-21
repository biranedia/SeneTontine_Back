package com.senetontine.repository;

import com.senetontine.model.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {
    Optional<Announcement> findTopByOrderByIdDesc();
    List<Announcement> findTop20ByOrderByIdDesc();
}
