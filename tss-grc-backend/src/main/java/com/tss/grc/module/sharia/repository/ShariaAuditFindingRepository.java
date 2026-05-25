package com.tss.grc.module.sharia.repository;

import com.tss.grc.module.sharia.entity.ShariaAuditFinding;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShariaAuditFindingRepository extends JpaRepository<ShariaAuditFinding, Long> {

    Page<ShariaAuditFinding> findByOrganisationIdOrderByRaisedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ShariaAuditFinding> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ShariaAuditFinding> findByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatusInAndSeverity(
            Long organisationId, java.util.Collection<String> statuses, String severity);
}
