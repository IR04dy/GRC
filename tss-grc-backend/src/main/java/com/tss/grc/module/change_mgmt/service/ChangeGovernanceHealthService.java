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
 * and an endpoint at {@code GET /api/v1/changes/governance-health}. It is
 * read-only and deliberately does <em>not</em> auto-create committees — a body with no
 * charter, chair or members could never reach quorum, so minutes could never be
 * approved and the gate would fail at the last step while appearing configured.
 *
 * <p>⚠⚠ <b>F2-437 — that sentence used to name "the platform-admin / production-readiness
 * surface", and the production-readiness page has been UNROUTED since Sprint 4 §3.3.</b>
 * {@code pages/production-readiness/ProductionReadinessPage.tsx} is still on disk and
 * {@code App.tsx} carries no {@code path="/production-readiness"} — only two comments recording
 * the cull. So the sentence pointed a reader at a page nobody can open, and measured
 * 2026-09-02 <b>nothing in the frontend references this endpoint at all</b>. The WARN below is
 * therefore the whole of the reporting half — which this class's own next paragraph says is not
 * enough: <em>"a WARN is not a control"</em>. The ENFORCEMENT half (A17-D2) is unaffected and is
 * what makes the gap block work rather than downgrade it; only the reporting half is unread.
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
     * S8b / CE-12 — CAB throughput for §12's KPI pack, as a finished aggregate.
     *
     * <p><b>Why this lives here rather than behind {@code CommitteePort}.</b> This service already
     * owns "what does the committee engine say about change authority?" and already answers it with
     * native SQL over {@code admin.committee*} — A17-D2 and F2-96 both shipped that way. Putting the
     * neighbouring metric behind a new port method would give the platform <em>two</em> mechanisms
     * for one concern, which is the thing {@code CommitteePort}'s own javadoc warns against. One
     * mechanism, stated once. ⚠ Note the honest cost: {@code ChangeModuleBoundaryTest} inspects
     * <em>imports</em>, and native SQL is not an import, so this route crosses the module boundary
     * without the ratchet seeing it (filed as F2-108).
     *
     * <p><b>Scoped to the tenant's gate-mapped committees, never to every committee.</b> Measured
     * 2026-08-04: CAB and ECAB together hold <b>4 of 34</b> meetings in dev and 4 of 32 in prod — the
     * other thirty belong to the audit committee, the Sharia board and the rest. A "CAB quorum-met
     * rate" over all committees would be a figure about bodies that never see a change: S7b's trap
     * (a queue whose population was never defined) in KPI form.
     *
     * @param quorumBasisRecorded of {@code meetingsHeld}, how many recorded a quorum basis at all
     * @param quorumMet           of {@code quorumBasisRecorded}, how many were quorate
     * @param quorumBasisAsserted of {@code quorumBasisRecorded}, how many were <em>asserted</em>
     *                            rather than computed from the roster. Measured: <b>2 of 2</b> in both
     *                            databases, which is why {@link ChangeKpi} needs a provenance axis at
     *                            all — the rate is 100% and it rests entirely on what a human typed.
     */
    public record CabThroughput(
        List<Long> committeeIds,
        long meetingsHeld,
        long meetingsScheduled,
        long meetingsTotal,
        long quorumBasisRecorded,
        long quorumMet,
        long quorumBasisAsserted,
        long resolutions,
        long resolutionsDecided,
        long resolutionsDeferred,
        long attendanceRowsRecorded,
        long decidedReferrals,
        long memberTerms,
        long termsExpiringSoon
    ) {
        /** The horizon "expiring soon" means. 90 days is one quarter — a board's planning cycle. */
        public static final int EXPIRY_HORIZON_DAYS = 90;

        /** Nothing mapped — A17-D2's unconfigured tenant. Distinct from "mapped and idle". */
        public static CabThroughput unconfigured() {
            return new CabThroughput(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
        public boolean isUnconfigured() { return committeeIds.isEmpty(); }
    }

    /**
     * <b>The single definition of "this tenant's gate is configured".</b> Correlated on
     * {@code :orgCol} / {@code :gateCol} so both the aggregate report and the single-gate
     * lookup below use the same text. A gate is configured when a live gate-map row points
     * at a committee that exists, is not soft-deleted, is not {@code DISSOLVED} — the third
     * clause matching what {@code CommitteeReferralService.refer} actually enforces — and
     * <b>has at least one ACTIVE voting seat</b>.
     *
     * <p><b>F2-96 — why the last clause was added, and why it belongs HERE.</b> Measured
     * 2026-08-02 in dev and production alike: committee 13, the committee org 1's CAB gate
     * points at, had <b>zero ACTIVE voting seats</b> in both databases, and this method
     * reported {@code healthy: true}. The predicate answered "is a committee mapped?" while
     * the screen it feeds claims "changes routed to this authority can be authorised". A
     * committee with no voting seat can never be quorate — {@code computeQuorum} sets
     * {@code quorumMet = 0} whenever {@code totalVoting == 0} — so a referred change routed
     * there is undecidable, and a non-referred one was being recorded as <em>CAB approved</em>
     * in the name of a body with no members.
     *
     * <p>It belongs in this constant rather than in a second check because F2-62's lesson is
     * that <b>a report describing access must share its predicate with the enforcement
     * point</b>. {@link #gateConfigurationFault} feeds
     * {@code ChangeManagementService.requireConfiguredChangeAuthority}, so widening the
     * definition here makes the report honest <em>and</em> the gate fail closed in the same
     * edit. Two predicates would have let them drift again, which is the defect this fixes.
     *
     * <p>⚠ The consequence is deliberate and it is not cosmetic: a tenant whose mapped
     * committee has an empty roster now has its CAB/ECAB approvals <b>refused</b> until
     * members are appointed. ADR-017 D-2 accepted exactly this trade — fail closed, with a
     * guided onboarding moment naming the screen — and {@link #ROSTER_EMPTY_REMEDY} names
     * appointing members rather than re-mapping the gate, because the mapping is correct.
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
                   AND c.status <> 'DISSOLVED'
                   AND EXISTS (SELECT 1
                                 FROM admin.committee_member_terms t
                                WHERE t.committee_id = c.id
                                  AND t.organisation_id = c.organisation_id
                                  AND t.renewal_status = 'ACTIVE'
                                  AND t.is_voting_member = 1))
        """;

    /**
     * Does a live gate-map row point at a live committee, ignoring whether it can be quorate?
     * Used only to tell {@link #ROSTER_EMPTY} apart from {@link #COMMITTEE_UNAVAILABLE}, so
     * the remedy can name the right screen.
     */
    private static final String COMMITTEE_LIVE_SQL = """
        SELECT COUNT(*)
          FROM admin.committee_gate_map g
          JOIN admin.committees c
            ON c.id = g.committee_id AND c.organisation_id = g.organisation_id
         WHERE g.organisation_id = ?1 AND g.gate = ?2
           AND g.is_deleted = 0 AND c.is_deleted = 0 AND c.status <> 'DISSOLVED'
        """;

    static final String ROSTER_EMPTY_REMEDY =
        "The gate is mapped to a live committee, but that committee has no ACTIVE voting member, "
      + "so it can never be quorate and no decision it records would be valid. Appoint voting "
      + "members to it on the Committees screen (" + ChangeAuthorityNotConfiguredException.REMEDY_SCREEN
      + " -> the committee -> Members), then retry. Re-mapping the gate is NOT the fix here: the "
      + "mapping is correct and the body is empty.";

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

        // Three faults, not two — the remedy differs (F2-96). Narrowest first: a live committee
        // that simply has nobody on it is a different administrative action from a missing or
        // dissolved one, and collapsing them would send the operator to re-map a correct mapping.
        Number live = (Number) em.createNativeQuery(COMMITTEE_LIVE_SQL)
            .setParameter(1, organisationId).setParameter(2, gate).getSingleResult();
        if (live != null && live.longValue() > 0)
            return ChangeAuthorityNotConfiguredException.ROSTER_EMPTY;

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

    /**
     * S8b / CE-12 — one aggregate row of CAB/ECAB throughput for the current tenant.
     *
     * <p>Read-only, one statement, scoped by the tenant's own gate map. Returns
     * {@link CabThroughput#unconfigured()} when the tenant has mapped no committee — distinct from a
     * mapped-but-idle CAB, because the KPI layer must not render "0 meetings held" as a performance
     * figure when the real fact is that no authority is configured (A17-D2).
     *
     * <p>⚠ {@code quorum_source} is read, not just {@code quorum_met}. S7f established why: 32 of 34
     * meetings carry an <em>asserted</em> basis, so a quorum-met rate without that count would present
     * a typed claim as a measured control.
     */
    @Transactional(readOnly = true)
    public CabThroughput cabThroughput() {
        Long org = TenantContext.getCurrentTenant();
        if (org == null) return CabThroughput.unconfigured();

        @SuppressWarnings("unchecked")
        List<Number> ids = em.createNativeQuery(GATE_COMMITTEES_SQL)
            .setParameter(1, org).getResultList();
        List<Long> committeeIds = ids.stream().filter(java.util.Objects::nonNull)
            .map(Number::longValue).distinct().sorted().toList();
        if (committeeIds.isEmpty()) return CabThroughput.unconfigured();

        Object[] r = (Object[]) em.createNativeQuery(CAB_THROUGHPUT_SQL)
            .setParameter(1, org).getSingleResult();
        return new CabThroughput(committeeIds,
            n(r[0]), n(r[1]), n(r[2]), n(r[3]), n(r[4]), n(r[5]),
            n(r[6]), n(r[7]), n(r[8]), n(r[9]), n(r[10]), n(r[11]), n(r[12]));
    }

    private static long n(Object o) { return o == null ? 0L : ((Number) o).longValue(); }

    /** The tenant's gate-mapped committees — the only bodies whose meetings are CAB meetings. */
    private static final String GATE_COMMITTEES_SQL = """
        SELECT gm.committee_id
          FROM admin.committee_gate_map gm
         WHERE gm.organisation_id = ?1 AND gm.is_deleted = 0 AND gm.committee_id IS NOT NULL
        """;

    /**
     * Every CAB figure in one statement, over the gate-mapped committees only.
     *
     * <p>⚠ Each sub-select filters {@code is_deleted} where the table has it — landmine #6, and the
     * mistake I had just made in this slice's own census (a bare {@code COUNT(*)} that swept in 25
     * soft-deleted evidence rows). {@code committee_resolutions} and {@code committee_member_terms}
     * carry no {@code is_deleted} column, which is why they are not filtered: verified against
     * {@code sys.columns}, not assumed from the others.
     */
    private static final String CAB_THROUGHPUT_SQL = """
        WITH cab AS (
            SELECT gm.committee_id AS cid
              FROM admin.committee_gate_map gm
             WHERE gm.organisation_id = ?1 AND gm.is_deleted = 0 AND gm.committee_id IS NOT NULL
        ), mtg AS (
            SELECT m.* FROM admin.committee_meetings m
             WHERE m.organisation_id = ?1 AND m.is_deleted = 0
               AND m.committee_id IN (SELECT cid FROM cab)
        )
        SELECT
          (SELECT COUNT(*) FROM mtg WHERE status IN ('MINUTES_APPROVED','COMPLETED')),
          (SELECT COUNT(*) FROM mtg WHERE status = 'SCHEDULED'),
          (SELECT COUNT(*) FROM mtg),
          (SELECT COUNT(*) FROM mtg WHERE status IN ('MINUTES_APPROVED','COMPLETED')
                                      AND quorum_source IS NOT NULL),
          (SELECT COUNT(*) FROM mtg WHERE status IN ('MINUTES_APPROVED','COMPLETED')
                                      AND quorum_source IS NOT NULL AND quorum_met = 1),
          (SELECT COUNT(*) FROM mtg WHERE status IN ('MINUTES_APPROVED','COMPLETED')
                                      AND quorum_source IS NOT NULL AND quorum_source <> 'COMPUTED'),
          (SELECT COUNT(*) FROM admin.committee_resolutions r
             WHERE r.organisation_id = ?1 AND r.meeting_id IN (SELECT id FROM mtg)),
          (SELECT COUNT(*) FROM admin.committee_resolutions r
             WHERE r.organisation_id = ?1 AND r.meeting_id IN (SELECT id FROM mtg)
               AND r.outcome IN ('APPROVED','REJECTED','NOTED')),
          (SELECT COUNT(*) FROM admin.committee_resolutions r
             WHERE r.organisation_id = ?1 AND r.meeting_id IN (SELECT id FROM mtg)
               AND r.outcome = 'DEFERRED'),
          (SELECT COUNT(*) FROM admin.committee_meeting_attendee a
             WHERE a.organisation_id = ?1 AND a.is_deleted = 0
               AND a.committee_meeting_id IN (SELECT id FROM mtg)),
          (SELECT COUNT(*) FROM admin.committee_referral f
             WHERE f.organisation_id = ?1 AND f.is_deleted = 0 AND f.source_module = 'CHANGE'
               AND f.status = 'DECIDED'),
          (SELECT COUNT(*) FROM admin.committee_member_terms t
             WHERE t.organisation_id = ?1 AND t.committee_id IN (SELECT cid FROM cab)
               AND t.ended_at IS NULL),
          (SELECT COUNT(*) FROM admin.committee_member_terms t
             WHERE t.organisation_id = ?1 AND t.committee_id IN (SELECT cid FROM cab)
               AND t.ended_at IS NULL AND t.term_ends_at IS NOT NULL
               AND t.term_ends_at <= DATEADD(DAY, 90, SYSUTCDATETIME()))
        """;

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
                : rosterOnly(gaps) ? ROSTER_EMPTY_REMEDY
                : "Constitute an ACTIVE committee for this organisation and map it to the gate "
                  + "on the Committees screen (" + ChangeAuthorityNotConfiguredException.REMEDY_SCREEN
                  + " → Change authority), or call "
                  + ChangeAuthorityNotConfiguredException.REMEDY_API + ". Until then, changes whose "
                  + "authorisation matrix demands this gate CANNOT be approved — the approval is "
                  + "refused rather than falling back to the legacy approval tally."
        );
    }

    /**
     * True when every gap is a mapped-but-empty committee, so the remedy is "appoint members"
     * rather than "map a committee". Checked per gap because a tenant can have one of each, and
     * a remedy that names the wrong screen is the dead-end error ADR-017 D-2 rules out.
     */
    private boolean rosterOnly(List<GateGap> gaps) {
        Long org = TenantContext.getCurrentTenant();
        return !gaps.isEmpty() && gaps.stream().allMatch(g ->
            ChangeAuthorityNotConfiguredException.ROSTER_EMPTY.equals(
                gateConfigurationFault(org, g.gate())));
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
