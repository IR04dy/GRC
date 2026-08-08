package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskAcceptance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RiskAcceptanceRepository extends JpaRepository<RiskAcceptance, Long> {

    List<RiskAcceptance> findByOrganisationIdAndRiskIdOrderByCreatedAtDesc(Long organisationId, Long riskId);

    Optional<RiskAcceptance> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Expiry sweep (system-wide, all tenants) — acceptances in a status past their expiry. */
    List<RiskAcceptance> findByStatusAndExpiresAtLessThan(String status, LocalDate date);

    /**
     * F2-164 — does this risk carry a <b>live formal acceptance</b>: an APPROVED
     * record whose time-box has not lapsed?
     *
     * <p>This is what {@code RiskService.close}'s mandatory-treatment exemption
     * reads. It used to read {@code risk.status == 'ACCEPTED'}, which is a
     * <i>projection</i> of the record and could be produced without one — so a
     * HIGH risk could be exempted from the treatment gate by a bare status flip.
     * The record is the authority; the status is its shadow.
     *
     * <p>⚠ {@code expires_at &gt;= today} matters as much as {@code APPROVED}.
     * {@code expireSweep} reopens a risk whose acceptance lapses, but a sweep
     * that has not yet run must not leave a stale exemption standing — the gate
     * fails closed rather than trusting the sweep's punctuality.
     */
    boolean existsByOrganisationIdAndRiskIdAndStatusAndExpiresAtGreaterThanEqual(
            Long organisationId, Long riskId, String status, LocalDate onOrAfter);

    /** E-K — audit-package posture: count of acceptances in a given status for the tenant. */
    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
