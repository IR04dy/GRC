package com.tss.grc.module.rrp.repository;

import com.tss.grc.module.rrp.entity.RrpCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RrpCycleRepository extends JpaRepository<RrpCycle, Long> {

    Page<RrpCycle> findByOrganisationIdOrderByFiscalYearDesc(
            Long organisationId, Pageable pageable);

    Optional<RrpCycle> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RrpCycle> findByReferenceAndOrganisationId(String reference, Long organisationId);

    Optional<RrpCycle> findFirstByOrganisationIdOrderByFiscalYearDesc(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
