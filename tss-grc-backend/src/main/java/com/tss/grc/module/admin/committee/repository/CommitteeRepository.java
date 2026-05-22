package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.Committee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommitteeRepository extends JpaRepository<Committee, Long> {
    Page<Committee> findByOrganisationIdOrderByNameEnAsc(Long organisationId, Pageable pageable);
    Page<Committee> findByOrganisationIdAndStatusOrderByNameEnAsc(Long organisationId, String status, Pageable pageable);
    Optional<Committee> findByIdAndOrganisationId(Long id, Long organisationId);
    Optional<Committee> findByOrganisationIdAndCode(Long organisationId, String code);

    /** All committees for an org — used by CommitteeScheduledSweep instead
     *  of findAll().filter(byOrg). With N tenants and M committees, the
     *  earlier pattern was O(N×M); this is O(M/N) per tenant. */
    List<Committee> findByOrganisationId(Long organisationId);
}
