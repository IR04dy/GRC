package com.tss.grc.module.privacy_lc.repository;

import com.tss.grc.module.privacy_lc.entity.DsrCase;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DsrCaseRepository extends JpaRepository<DsrCase, Long> {
    List<DsrCase> findByOrganisationIdOrderByFiledAtDesc(Long organisationId);
    Optional<DsrCase> findByOrganisationIdAndExternalId(Long organisationId, String externalId);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    /** Overdue = SLA past + still open (not DELIVERED/REJECTED/EXTENDED). */
    List<DsrCase> findByOrganisationIdAndStatusNotInAndSlaDeadlineBefore(Long organisationId, List<String> closedStatuses, LocalDate before);
}
