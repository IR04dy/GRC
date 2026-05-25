package com.tss.grc.module.regreporting.repository;

import com.tss.grc.module.regreporting.entity.RegReportEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RegReportEventRepository extends JpaRepository<RegReportEvent, Long> {

    List<RegReportEvent> findByFilingIdAndOrganisationIdOrderByOccurredAtDesc(
            Long filingId, Long organisationId);
}
