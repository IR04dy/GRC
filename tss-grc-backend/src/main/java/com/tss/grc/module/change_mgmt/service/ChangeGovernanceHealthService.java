package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.exception.ChangeAuthorityNotConfiguredException;
import com.tss.grc.common.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Configuration-health check for change authorisation (S4 / W1-4, finding F-4).
 *
 * <p>The change authorisation matrix says <em>which</em> authority must decide a change
 * (AUTO / CAB / ECAB); {@code admin.committee_gate_map} says <em>which committee</em>
 * that authority actually is. If the matrix demands CAB but no mapping exists, the
 * committee route cannot engage: {@code CommitteeReferralService.refer} throws "No
 * committee is configured for gate CAB", so in practice every change quietly falls back
 * to the legacy approval tally. The control looks present in code and is absent in
 * operation — which is exactly how F-4 went unnoticed: an empty configuration table
 * produces no error until somebody tries to refer a change.
 *
 * <p>This service makes that state loud instead of silent: a WARN per gap at startup,
 * and an endpoint the platform-admin / production-readiness surface can poll. It is
 * read-only and deliberately does <em>not</em> auto-create committees — a body with no
 * charter, chair or members could never reach quorum, so minutes could never be
 * approved and the gate would fail at the last step while appearing configured.
 *
 * <p><b>ADR-017 D-2 (A17-D2): this service now also owns the predicate that ENFORCES the
 * rule, not only the one that reports it.</b> F-4 was closed by making the gap loud, and a
 * WARN is not a control: measured on 2026-08-01, orgs 2 and 3 each had 11 matrix cells
 * demanding a committee, zero committees, and no gate-map row — and their changes still
 * reached {@code CAB_APPROVED}, because {@code ChangeManagementService} applied the
 * committee gate only when a referral already existed and otherwise fell through to the
 * legacy vote tally. The startup WARN had been describing that for a week.
 *
 * <p><b>Why {@link #CONFIGURED_PREDICATE} is one string interpolated into both queries.</b>
 * The gap report and the transition refusal must answer "is this gate configured?"
 * identically, or the platform will refuse an approval it reports as healthy — or, worse,
 * permit one it reports as broken. Two implementations that agree today are not the same
 * predicate; F2-62 cost exactly this (an access report whose predicate had drifted from the
 * enforcement point it described). The predicate also now includes the <b>committee's own
 * state</b>, which the report previously ignored: a row pointing at a {@code DISSOLVED}
 * committee read as healthy here while {@code CommitteeReferralService.refer} rejected it.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChangeGovernanceHealthService {

    private final EntityManager em;

    /** One authority gate that the matrix demands but no committee is mapped to. */
    public record GateGap(Long organisationId, String gate, long matrixRows) {}

    /**
     * <b>The single definition of "this tenant's gate is configured".</b> Correlated on
     * {@code :orgCol} / {@code :gateCol} so both the aggregate report and the single-gate
     * lookup below use the same text. A gate is configured when a live gate-map row points
     * at a committee that exists, is not soft-deleted, and is not {@code DISSOLVED} — the
     * last clause matching what {@code CommitteeReferralService.refer} actually enforces.
     */
    private static final String CONFIGURED_PREDICATE = """
        EXISTS (SELECT 1
                  FROM admin.committee_gate_map g
                  JOIN admin.committees c
                    ON c.id = g.committee_id
                   AND c.organisation_id = g.organisation_id
                 WHERE g.organisation_id = %s
                   AND g.gate = %s
                   AND g.is_deleted = 0
                   AND c.is_deleted = 0
                   AND c.status <> 'DISSOLVED')
        """;

    private static final String GAP_SQL = """
        SELECT m.organisation_id, m.gate, COUNT(*) AS matrix_rows
          FROM change_mgmt.change_authorisation_matrix m
         WHERE m.is_deleted = 0
           AND m.gate IN ('CAB', 'ECAB')
           AND NOT %s
         GROUP BY m.organisation_id, m.gate
         ORDER BY m.organisation_id, m.gate
        """.formatted(CONFIGURED_PREDICATE.formatted("m.organisation_id", "m.gate"));

    /** Single-gate form of {@link #CONFIGURED_PREDICATE}, for the enforcement path. */
    private static final String CONFIGURED_ONE_SQL =
        "SELECT CASE WHEN " + CONFIGURED_PREDICATE.formatted("?1", "?2") + " THEN 1 ELSE 0 END";

    /** Does a live gate-map row exist at all, regardless of the committee's state? */
    private static final String ROW_EXISTS_SQL = """
        SELECT COUNT(*) FROM admin.committee_gate_map
         WHERE organisation_id = ?1 AND gate = ?2 AND is_deleted = 0
        """;

    /**
     * Every tenant/gate combination that demands a committee but has none mapped.
     * Cross-tenant by design — this is an operator/administrator view, used by the
     * startup warning below. Read-only.
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public List<GateGap> unmappedGates() {
        List<Object[]> rows = em.createNativeQuery(GAP_SQL).getResultList();
        return rows.stream()
            .map(r -> new GateGap(((Number) r[0]).longValue(), (String) r[1], ((Number) r[2]).longValue()))
            .toList();
    }

    /**
     * ADR-017 D-2 enforcement side: the fault preventing {@code gate} from authorising in
     * {@code organisationId}, or {@code null} when the gate is properly configured.
     *
     * <p>Returns {@link ChangeAuthorityNotConfiguredException#NOT_MAPPED} when no row exists
     * and {@link ChangeAuthorityNotConfiguredException#COMMITTEE_UNAVAILABLE} when one does
     * but its committee cannot serve — two different administrator actions, so the caller is
     * told which. Cheap enough to run on every CAB-dependent transition: two indexed reads,
     * on a table with a unique index on {@code (organisation_id, gate)}.
     */
    @Transactional(readOnly = true)
    public String gateConfigurationFault(Long organisationId, String gate) {
        if (organisationId == null || gate == null) return null;
        Number ok = (Number) em.createNativeQuery(CONFIGURED_ONE_SQL)
            .setParameter(1, organisationId).setParameter(2, gate).getSingleResult();
        if (ok != null && ok.intValue() == 1) return null;
        Number rows = (Number) em.createNativeQuery(ROW_EXISTS_SQL)
            .setParameter(1, organisationId).setParameter(2, gate).getSingleResult();
        return (rows != null && rows.longValue() > 0)
            ? ChangeAuthorityNotConfiguredException.COMMITTEE_UNAVAILABLE
            : ChangeAuthorityNotConfiguredException.NOT_MAPPED;
    }

    /** True when {@code gate} can authorise in {@code organisationId}. */
    @Transactional(readOnly = true)
    public boolean isGateConfigured(Long organisationId, String gate) {
        return gateConfigurationFault(organisationId, gate) == null;
    }

    /** The calling tenant's gaps only — what the change module's own UI should show. */
    @Transactional(readOnly = true)
    public List<GateGap> unmappedGatesForCurrentTenant() {
        Long org = TenantContext.getCurrentTenant();
        return unmappedGates().stream()
            .filter(g -> org != null && org.equals(g.organisationId()))
            .toList();
    }

    /**
     * Health summary for the current tenant: {@code healthy} is false when any gate the
     * matrix demands has no committee behind it, i.e. changes routed to that authority
     * cannot actually be referred.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> currentTenantHealth() {
        List<GateGap> gaps = unmappedGatesForCurrentTenant();
        return Map.of(
            "healthy", gaps.isEmpty(),
            "unmappedGates", gaps.stream().map(GateGap::gate).toList(),
            "detail", gaps,
            // ADR-017 D-2: this sentence used to end "...fall back to the legacy approval
            // tally instead of a committee decision", which was an accurate description of a
            // fail-OPEN and is no longer true. The consequence is now a refusal, and the
            // remedy names the screen that fixes it, because a refusal an administrator
            // cannot act on is the dead-end error D-2 explicitly rules out.
            "remedy", gaps.isEmpty() ? ""
                : "Constitute an ACTIVE committee for this organisation and map it to the gate "
                  + "on the Committees screen (" + ChangeAuthorityNotConfiguredException.REMEDY_SCREEN
                  + " → Change authority), or call "
                  + ChangeAuthorityNotConfiguredException.REMEDY_API + ". Until then, changes whose "
                  + "authorisation matrix demands this gate CANNOT be approved — the approval is "
                  + "refused rather than falling back to the legacy approval tally."
        );
    }

    /**
     * Fail loudly at boot. A misconfigured gate is not an exception anywhere — it is an
     * absent row — so without this it stays invisible until someone refers a change.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnOnUnmappedGates() {
        try {
            List<GateGap> gaps = unmappedGates();
            if (gaps.isEmpty()) {
                log.info("[change-governance] committee gate map OK — every CAB/ECAB "
                    + "authority demanded by the authorisation matrix has a committee mapped.");
                return;
            }
            log.warn("[change-governance] {} authority gate(s) demand a committee but none can "
                + "authorise — approvals routed to them now FAIL CLOSED (ADR-017 D-2):", gaps.size());
            for (GateGap g : gaps) {
                log.warn("[change-governance]   org={} gate={} ({} matrix row(s) demand it) — "
                    + "map an ACTIVE committee via {} ({})",
                    g.organisationId(), g.gate(), g.matrixRows(),
                    ChangeAuthorityNotConfiguredException.REMEDY_SCREEN,
                    ChangeAuthorityNotConfiguredException.REMEDY_API);
            }
        } catch (Exception ex) {
            // Never let a health probe stop the application from starting.
            log.warn("[change-governance] gate-map health check skipped: {}", ex.getMessage());
        }
    }
}
