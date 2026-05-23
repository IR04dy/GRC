package com.tss.grc.module.integrations.splunk.repository;

import com.tss.grc.module.integrations.splunk.entity.SplunkHecOutboundLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SplunkHecOutboundLogRepository extends JpaRepository<SplunkHecOutboundLog, Long> {

    /** Most recent successful push for a given GRC record (used by /status endpoint). */
    SplunkHecOutboundLog findFirstByOrganisationIdAndHttpStatusOrderByCreatedAtDesc(
        Long organisationId, Integer httpStatus);

    /** Browse audit history for a specific GRC record. */
    Page<SplunkHecOutboundLog> findByOrganisationIdAndSourceModuleAndSourceIdOrderByCreatedAtDesc(
        Long organisationId, String sourceModule, String sourceId, Pageable pageable);
}
