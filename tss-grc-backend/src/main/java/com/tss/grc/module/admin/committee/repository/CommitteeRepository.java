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

    /**
     * F2-177 — the bodies of a given type that are actually constituted in this tenant, for the
     * {@code hasPermission('AUDIT_COMMITTEE','COMMITTEE','SEAT')} guard leg.
     *
     * <p>Type rather than id, because a guard is written once and every tenant's audit committee has its
     * own primary key. A list rather than an Optional, because nothing stops a tenant constituting two
     * bodies of one type — dev has <b>three</b> {@code IT_STEERING} committees, so an
     * {@code Optional}-shaped finder would have thrown
     * {@code IncorrectResultSizeDataAccessException} inside a {@code @PreAuthorize} and turned a
     * false guard leg into a 500.
     *
     * <p>{@code status} is a parameter and the caller passes {@code ACTIVE}: a {@code DISSOLVED}
     * committee confers no seat. Soft deletes are handled by the entity's {@code @Where(is_deleted = 0)}.
     */
    List<Committee> findByOrganisationIdAndCommitteeTypeAndStatus(
        Long organisationId, String committeeType, String status);

    /** All committees for an org — used by CommitteeScheduledSweep instead
     *  of findAll().filter(byOrg). With N tenants and M committees, the
     *  earlier pattern was O(N×M); this is O(M/N) per tenant. */
    List<Committee> findByOrganisationId(Long organisationId);
}
