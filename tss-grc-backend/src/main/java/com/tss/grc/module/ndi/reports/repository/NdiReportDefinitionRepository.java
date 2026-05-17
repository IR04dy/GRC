package com.tss.grc.module.ndi.reports.repository;

import com.tss.grc.module.ndi.reports.entity.NdiReportDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiReportDefinitionRepository extends JpaRepository<NdiReportDefinition, Long> {

    List<NdiReportDefinition> findByOrganisationIdOrderByReportCodeAsc(Long org);

    List<NdiReportDefinition> findByOrganisationIdAndReportTypeOrderByReportCodeAsc(Long org, String type);

    Optional<NdiReportDefinition> findByIdAndOrganisationId(Long id, Long org);
}
