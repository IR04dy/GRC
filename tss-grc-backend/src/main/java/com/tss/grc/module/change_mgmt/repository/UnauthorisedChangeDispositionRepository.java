package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.UnauthorisedChangeDisposition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * S8f / CE-11 — stored dispositions for derived reconciliation findings (V355).
 *
 * <p>Every query filters {@code organisationId} (CLAUDE.md: developer responsibility). The
 * soft-delete filter comes from {@code @Where} on the entity.
 */
public interface UnauthorisedChangeDispositionRepository
        extends JpaRepository<UnauthorisedChangeDisposition, Long> {

    /**
     * Every disposition in the tenant, loaded once and indexed by the finding key.
     *
     * <p>Read whole rather than per finding: the register derives its findings first and then attaches
     * dispositions, so a per-row lookup would be one query per finding for a table that holds at most
     * one row per (change, reason).
     */
    List<UnauthorisedChangeDisposition> findByOrganisationId(Long organisationId);

    /** The uniqueness the natural key enforces — re-dispositioning updates rather than appends. */
    Optional<UnauthorisedChangeDisposition> findByOrganisationIdAndChangeRequestIdAndReasonCode(
        Long organisationId, Long changeRequestId, String reasonCode);
}
