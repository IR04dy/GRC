package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.CriticalProcess;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CriticalProcessRepository extends JpaRepository<CriticalProcess, Long> {
    Page<CriticalProcess> findByOrganisationId(Long organisationId, Pageable pageable);
}
