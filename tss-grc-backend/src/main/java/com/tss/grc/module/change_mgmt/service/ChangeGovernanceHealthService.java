package com.tss.grc.module.change_mgmt.service;

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
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChangeGovernanceHealthService {

    private final EntityManager em;

    /** One authority gate that the matrix demands but no committee is mapped to. */
    public record GateGap(Long organisationId, String gate, long matrixRows) {}

    private static final String GAP_SQL = """
        SELECT m.organisation_id, m.gate, COUNT(*) AS matrix_rows
          FROM change_mgmt.change_authorisation_matrix m
         WHERE m.is_deleted = 0
           AND m.gate IN ('CAB', 'ECAB')
           AND NOT EXISTS (
                 SELECT 1 FROM admin.committee_gate_map g
                  WHERE g.organisation_id = m.organisation_id
                    AND g.gate = m.gate
                    AND g.is_deleted = 0)
         GROUP BY m.organisation_id, m.gate
         ORDER BY m.organisation_id, m.gate
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
            "remedy", gaps.isEmpty() ? ""
                : "Constitute an ACTIVE committee for this organisation and map it to the "
                  + "gate (admin.committee_gate_map), or pass committeeId explicitly when "
                  + "referring a change. Until then these changes fall back to the legacy "
                  + "approval tally instead of a committee decision."
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
            log.warn("[change-governance] {} authority gate(s) demand a committee but none is "
                + "mapped — changes routed to them CANNOT be committee-gated and will fall back "
                + "to the legacy approval tally:", gaps.size());
            for (GateGap g : gaps) {
                log.warn("[change-governance]   org={} gate={} ({} matrix row(s) demand it) — "
                    + "map an ACTIVE committee in admin.committee_gate_map",
                    g.organisationId(), g.gate(), g.matrixRows());
            }
        } catch (Exception ex) {
            // Never let a health probe stop the application from starting.
            log.warn("[change-governance] gate-map health check skipped: {}", ex.getMessage());
        }
    }
}
