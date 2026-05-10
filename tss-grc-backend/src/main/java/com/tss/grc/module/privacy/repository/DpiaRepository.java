package com.tss.grc.module.privacy.repository;

import com.tss.grc.module.privacy.entity.Dpia;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DpiaRepository extends JpaRepository<Dpia, Long> {
    Page<Dpia> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndStatus(Long organisationId, String status);
    java.util.List<Dpia> findByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
    long countByOrganisationIdAndFrameworkId(Long organisationId, Long frameworkId);
}
