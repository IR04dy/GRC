package com.tss.grc.module.compliance.repository;

import com.tss.grc.module.compliance.entity.RegulatoryChange;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegulatoryChangeRepository extends JpaRepository<RegulatoryChange, Long> {
    Page<RegulatoryChange> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
