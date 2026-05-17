package com.tss.grc.module.audit_lc.repository;

import com.tss.grc.module.audit_lc.entity.WorkingPaper;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkingPaperRepository extends JpaRepository<WorkingPaper, Long> {

    List<WorkingPaper> findByOrganisationIdOrderByCreatedAtDesc(Long org);

    List<WorkingPaper> findByOrganisationIdAndEngagementCodeOrderByRefNumberAsc(Long org, String engagementCode);

    List<WorkingPaper> findByOrganisationIdAndStatusOrderByCreatedAtDesc(Long org, String status);

    Optional<WorkingPaper> findByOrganisationIdAndExternalId(Long org, String externalId);
}
