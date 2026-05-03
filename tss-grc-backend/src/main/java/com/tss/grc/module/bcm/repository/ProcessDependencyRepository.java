package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.ProcessDependency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProcessDependencyRepository extends JpaRepository<ProcessDependency, Long> {
    List<ProcessDependency> findByOrganisationId(Long organisationId);
    List<ProcessDependency> findByOrganisationIdAndProcessId(Long organisationId, Long processId);
}
