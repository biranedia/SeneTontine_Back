package com.senetontine.repository;

import com.senetontine.model.IncidentReport;
import com.senetontine.model.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
    // File admin, plafonnee comme toutes les autres listes de l'app.
    List<IncidentReport> findTop200ByOrderByIdDesc();
    List<IncidentReport> findTop200ByStatusOrderByIdDesc(IncidentStatus status);

    // "Mes signalements" cote membre.
    List<IncidentReport> findByReporterIdOrderByIdDesc(Long reporterId);

    long countByStatus(IncidentStatus status);
}
