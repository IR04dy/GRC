package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import com.tss.grc.module.change_mgmt.entity.ChangeModel;
import com.tss.grc.module.change_mgmt.entity.ChangeModelUsage;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.entity.UnauthorisedChangeDisposition;
import com.tss.grc.module.change_mgmt.model.RatificationStates;
import com.tss.grc.module.change_mgmt.repository.*;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * S8f / CE-11 — the unauthorised-change register: reconciles what each change <b>required</b> for
 * authorisation against what the platform can <b>show</b> it received.
 *
 * <p>Closes G-11 (<b>High</b>): <i>"Unauthorised changes undetectable — no reconciliation of
 * implemented vs authorised."</i>
 *
 * <h3>⚠ The plan named a source that does not exist, and that changed the deliverable</h3>
 * CE-11 is written as <i>"unapproved changes detected from configuration drift"</i>. Measured first,
 * as every slice in this programme now is: there is <b>no</b> CMDB, no configuration-item table, no
 * baseline table and no drift table anywhere in the schema — the only table matching {@code %drift%}
 * is {@code ai_governance.ml_model_drift_observation}, which is about ML models — and the
 * {@code integrations} schema holds <b>no tables at all</b>, so nothing feeds the platform a record of
 * what actually changed in production. Building drift detection would have meant inventing its input.
 *
 * <p>What <em>does</em> exist is the other and more useful half, complete in both databases: the
 * authorisation each change required ({@code change_authorisation_matrix}), the authorisation it
 * received ({@code change_approvals}, 31 rows) and whether it went ahead ({@code status}). That is a
 * genuine implemented-versus-authorised reconciliation, it is what G-11 actually asks for, and on the
 * seeded data it finds real failures — including an {@code EMERGENCY}/{@code CRITICAL} change sitting
 * at {@code IMPLEMENTED} on an {@code ECAB} cell demanding two approvals with one recorded.
 *
 * <h3>What it cannot see is published, not omitted</h3>
 * {@link #UNDETECTABLE} rides in every response. A register that quietly covers eight of ten failure
 * modes reads, to the operator and the auditor, exactly like one that covers ten — which is the
 * mistake S8b found seven times over in §12's KPI list and F2-62's shape before that. So the two
 * classes this reconciliation is blind to are named in the payload with the reason each is blind:
 *
 * <ul>
 *   <li><b>An unticketed production change.</b> Nothing was recorded, so no reconciliation over
 *       recorded rows can find it. Needs the drift feed that does not exist.</li>
 *   <li><b>Implementation during a freeze window.</b> The predicate is
 *       {@code implementation_started} inside an active window — and that column is NULL on
 *       <b>all 17</b> changes in dev, because it is only written by the {@code IMPLEMENTING}
 *       transition and every row was seeded straight past it. Comparing NULL yields UNKNOWN, which
 *       filters the row out and would report every change compliant forever. That is exactly the
 *       defect A17-D3 hit with {@code ratification_due_at}, so it is declared rather than computed.</li>
 * </ul>
 *
 * <h3>⚠ There are TWO authorisation routes, and reading one table would have condemned the other</h3>
 * {@code CAB_APPROVED} is reachable either by recorded {@code change_approvals} votes <b>or</b>, for a
 * referred change, by the committee's carried resolution — which is the system of record for that
 * route and writes <b>no</b> vote rows in this module at all (Track D). The first draft of this class
 * counted votes only, and would therefore have reported every committee-routed change as having no
 * authorisation on record: the route S6 and S7 exist to provide, flagged as the defect. Dev holds two
 * such changes (the {@code W15-GP-} golden-path rows), so the register's headline number would have
 * been wrong from its first read.
 *
 * <p>So {@link #committeeAuthorised} asks the engine through {@link CommitteePort}, and an
 * authorisation check is a union over every route that can authorise — the same rule as a read guard
 * being a union over every route that serves the record.
 *
 * <h3>Findings derived, dispositions stored</h3>
 * See {@code UnauthorisedChangeFinding} and {@link UnauthorisedChangeDisposition}. A disposition never
 * removes a finding, and the KPI counts every authority gap regardless of disposition — otherwise a
 * clean dashboard would be reachable by signing findings off rather than by fixing them.
 */
@Slf4j
@Service
public class UnauthorisedChangeRegisterService {

    /** Declared scope, in the payload, so a consumer can tell this register from A17-D3's narrower sink. */
    public static final String SCOPE = "RECORDED_CHANGES_VS_AUTHORISATION_MATRIX";

    /** Blind spots, each with the measured reason it is one. Keys are i18n suffixes, not prose. */
    public static final List<String> UNDETECTABLE = List.of(
        "UNTICKETED_PRODUCTION_CHANGE", "IMPLEMENTED_DURING_FREEZE");

    /**
     * Statuses meaning the change has passed the point where authorisation should already exist.
     *
     * <p>{@code CAB_APPROVED} is in the set deliberately: a change that reached "approved" with no
     * recorded vote is the reconciliation failing, and waiting for it to be implemented before saying
     * so would turn a catch into a breach. Dev holds exactly that case — change 229.
     */
    public static final List<String> PAST_AUTHORISATION = List.of(
        "CAB_APPROVED", "EMERGENCY_APPROVED", "SCHEDULED",
        "IMPLEMENTING", "IMPLEMENTED", "POST_REVIEW", "ROLLED_BACK", "CLOSED");

    /** Statuses meaning the change has actually taken effect. Drives severity, not membership. */
    public static final List<String> EFFECTIVE = List.of(
        "IMPLEMENTING", "IMPLEMENTED", "POST_REVIEW", "ROLLED_BACK", "CLOSED");

    private final ChangeRequestRepository changeRepo;
    private final ChangeApprovalRepository approvalRepo;
    private final ChangeAuthorisationMatrixRepository matrixRepo;
    private final UnauthorisedChangeDispositionRepository dispositionRepo;
    private final ChangeModelUsageRepository usageRepo;
    private final ChangeModelRepository modelRepo;
    /** The second authorisation route. Absent on a deployment without the committee engine. */
    private final CommitteePort committeePort;

    public UnauthorisedChangeRegisterService(
            ChangeRequestRepository changeRepo,
            ChangeApprovalRepository approvalRepo,
            ChangeAuthorisationMatrixRepository matrixRepo,
            @Autowired(required = false) UnauthorisedChangeDispositionRepository dispositionRepo,
            @Autowired(required = false) ChangeModelUsageRepository usageRepo,
            @Autowired(required = false) ChangeModelRepository modelRepo,
            @Autowired(required = false) CommitteePort committeePort) {
        this.changeRepo = changeRepo;
        this.approvalRepo = approvalRepo;
        this.matrixRepo = matrixRepo;
        this.dispositionRepo = dispositionRepo;
        this.usageRepo = usageRepo;
        this.modelRepo = modelRepo;
        this.committeePort = committeePort;
    }

    /** The register: findings, the population they were found in, and what could not be looked for. */
    public record Register(
        String scope,
        List<UnauthorisedChangeFinding> findings,
        long population,
        long authorityGaps,
        long unattributed,
        long open,
        long disposed,
        long autoAuthorisedEffective,
        Map<String, Long> byReason,
        Map<String, Long> bySeverity,
        List<String> undetectable
    ) { }

    // ── the reconciliation ───────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Register register() {
        Long org = TenantContext.getCurrentTenant();
        LocalDateTime now = LocalDateTime.now();

        List<ChangeRequest> population = changeRepo.findByStatuses(org, PAST_AUTHORISATION);

        // The cell for each (type x risk), read once. A missing cell is a finding, not a default.
        Map<String, ChangeAuthorisationMatrix> cells = new HashMap<>();
        for (ChangeAuthorisationMatrix m : matrixRepo.findMatrixRanked(org))
            cells.put(cellKey(m.getChangeType(), m.getRiskLevel()), m);

        Map<Long, Long> approveCounts = approveCountsFor(population);
        Map<String, UnauthorisedChangeDisposition> dispositions = dispositionsByKey(org);
        Map<Long, ChangeModelUsage> usages = modelUsagesFor(org);

        List<UnauthorisedChangeFinding> findings = new ArrayList<>();
        long autoEffective = 0;

        for (ChangeRequest c : population) {
            boolean effective = EFFECTIVE.contains(c.getStatus());
            ChangeAuthorisationMatrix cell = cells.get(cellKey(c.getChangeType(), c.getRiskLevel()));
            int approvals = approveCounts.getOrDefault(c.getId(), 0L).intValue();
            boolean stamped = c.getCabDecisionAt() != null;

            if (cell == null) {
                // Fail closed: with no cell there is no requirement to compare against, and an
                // unverifiable requirement must not read as a satisfied one (S6b-i).
                findings.add(finding(c, UnauthorisedChangeFinding.NO_AUTHORISATION_POLICY,
                    effective, null, null, approvals, stamped));
                continue;
            }

            int min = cell.getMinApprovals() == null ? 0 : cell.getMinApprovals();

            // ⚠ CAB_APPROVED has TWO legitimate routes and this reconciliation reads only one of the
            // tables. A referred change is decided by the committee's carried resolution — the system
            // of record (Track D) — and that route writes NO change_approvals rows at all. Checking
            // votes alone would report every committee-routed change as unauthorised, i.e. would
            // report the route S6 and S7 exist to provide as the defect. An authorisation check has
            // to be a union over every route that can authorise, exactly as a read guard has to be a
            // union over every route that serves the record.
            if (committeeAuthorised(c, min)) {
                // Attribution still matters, and here it is genuinely present: the resolution names
                // the meeting and the tally. Nothing to report.
                continue;
            }

            if (approvals < min) {
                // Zero votes and some-but-too-few are different governance failures. A stamp does not
                // close the gap either way — a stamp is an attribution, not a vote, and letting it
                // count would let the status column authorise itself.
                findings.add(finding(c,
                    approvals == 0 ? UnauthorisedChangeFinding.NO_AUTHORISATION_RECORD
                                   : UnauthorisedChangeFinding.INSUFFICIENT_APPROVALS,
                    effective, cell.getGate(), min, approvals, stamped));
            } else if (min > 0 && !stamped) {
                // Authority is present; only the platform's own record of who exercised it is missing.
                // Never an authority gap — see UnauthorisedChangeFinding#isAuthorityGap.
                findings.add(finding(c, UnauthorisedChangeFinding.UNATTRIBUTED_AUTHORISATION,
                    effective, cell.getGate(), min, approvals, stamped));
            }

            // S8e: a model authorises only while it is a live pre-approval. Retired, suspended or
            // expired, it authorises nothing — and the change went ahead anyway.
            ChangeModelUsage usage = usages.get(c.getId());
            if (usage != null && !modelWasLiveAt(usage, now))
                findings.add(finding(c, UnauthorisedChangeFinding.EXPIRED_MODEL_APPLIED,
                    effective, cell.getGate(), min, approvals, stamped));

            // A17-D3 owns this predicate. Mirrored, never recomputed: two registers that compute the
            // same breach independently are two registers that can disagree in front of an auditor.
            if (RatificationStates.PENDING.equals(c.getRatificationState())
                && c.getRatificationDueAt() != null && now.isAfter(c.getRatificationDueAt()))
                findings.add(finding(c, UnauthorisedChangeFinding.RATIFICATION_BREACHED,
                    effective, cell.getGate(), min, approvals, stamped));

            // Not a finding — the tenant's own matrix says AUTO needs nobody. Published as a count
            // because "took effect on a type label alone" is a fact a CAB chair should be able to see,
            // and S8e exists because that label was all five STANDARD changes had behind them.
            if ("AUTO".equals(cell.getGate()) && min == 0 && effective) autoEffective++;
        }

        findings.replaceAll(f -> f.withDisposition(dispositions.get(f.key())));
        findings.sort(Comparator
            .comparing((UnauthorisedChangeFinding f) -> f.isOpen() ? 0 : 1)
            .thenComparing(f -> UnauthorisedChangeFinding.HIGH.equals(f.severity()) ? 0 : 1)
            .thenComparing(UnauthorisedChangeFinding::changeId));

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (String r : UnauthorisedChangeFinding.REASON_CODES) byReason.put(r, 0L);
        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (String s : UnauthorisedChangeFinding.SEVERITIES) bySeverity.put(s, 0L);
        for (UnauthorisedChangeFinding f : findings) {
            byReason.merge(f.reasonCode(), 1L, Long::sum);
            bySeverity.merge(f.severity(), 1L, Long::sum);
        }

        return new Register(SCOPE, findings, population.size(),
            findings.stream().filter(UnauthorisedChangeFinding::isAuthorityGap).count(),
            byReason.getOrDefault(UnauthorisedChangeFinding.UNATTRIBUTED_AUTHORISATION, 0L),
            findings.stream().filter(UnauthorisedChangeFinding::isOpen).count(),
            findings.stream().filter(f -> !f.isOpen()).count(),
            autoEffective, byReason, bySeverity, UNDETECTABLE);
    }

    /**
     * The count the KPI publishes: changes with a genuine authority gap.
     *
     * <p>⚠ Counts <b>changes</b>, not findings — one change failing for two reasons is one
     * unauthorised change, and counting findings would inflate the compliance figure. And it counts
     * disposed gaps too: see {@link UnauthorisedChangeDisposition} for why a dashboard that clears on
     * sign-off is worse than no dashboard.
     */
    @Transactional(readOnly = true)
    public long authorityGapChangeCount() {
        return register().findings().stream()
            .filter(UnauthorisedChangeFinding::isAuthorityGap)
            .map(UnauthorisedChangeFinding::changeId)
            .distinct().count();
    }

    /** The population the count is a fraction of — changes past their authorisation point. */
    @Transactional(readOnly = true)
    public long populationSize() {
        return changeRepo.findByStatuses(TenantContext.getCurrentTenant(), PAST_AUTHORISATION).size();
    }

    // ── disposition ──────────────────────────────────────────────────────────────────────────────

    /**
     * Record an investigator's outcome for one finding.
     *
     * <p>{@code disposedBy*} and {@code disposedAt} come from the authenticated principal and the
     * server clock, never from the caller — which is what makes {@code ck_ucd_disposed} sufficient
     * rather than decorative (S8e's lesson: a CHECK tests that fields agree, not who set them).
     *
     * <p>Refuses a disposition for a finding that is not currently derivable. Without that check the
     * table would accumulate judgements about reconciliation failures that no longer exist — or never
     * did — and the register would show dispositions attached to nothing.
     */
    @Transactional
    public UnauthorisedChangeDisposition dispose(Long changeId, String reasonCode, String state,
                                                 String note, Long actorId, String actorName) {
        Long org = TenantContext.getCurrentTenant();
        if (dispositionRepo == null)
            throw new IllegalStateException("Disposition storage is not available (V355 not applied)");
        if (!UnauthorisedChangeFinding.REASON_CODES.contains(reasonCode))
            throw new IllegalArgumentException("Unknown reason code: " + reasonCode
                + ". One of " + UnauthorisedChangeFinding.REASON_CODES);
        if (!UnauthorisedChangeDisposition.STATES.contains(state))
            throw new IllegalArgumentException("Unknown disposition state: " + state
                + ". One of " + UnauthorisedChangeDisposition.STATES);

        String key = changeId + ":" + reasonCode;
        boolean stillFound = register().findings().stream().anyMatch(f -> f.key().equals(key));
        if (!stillFound)
            throw new IllegalArgumentException(
                "No open reconciliation finding " + key + " for this organisation. A disposition "
              + "records a judgement about a finding that exists; if the finding has been resolved by "
              + "recording the missing authority, there is nothing left to dispose.");

        UnauthorisedChangeDisposition d = dispositionRepo
            .findByOrganisationIdAndChangeRequestIdAndReasonCode(org, changeId, reasonCode)
            .orElseGet(() -> {
                UnauthorisedChangeDisposition fresh = new UnauthorisedChangeDisposition();
                fresh.setOrganisationId(org);
                fresh.setChangeRequestId(changeId);
                fresh.setReasonCode(reasonCode);
                fresh.setCreatedAt(LocalDateTime.now());
                return fresh;
            });
        d.setState(state);
        d.setNote(note == null || note.isBlank() ? null : note.trim());
        d.setDisposedById(actorId);
        d.setDisposedByName(actorName == null || actorName.isBlank() ? "unknown" : actorName.trim());
        d.setDisposedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        return dispositionRepo.save(d);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    private static String cellKey(String type, String risk) { return type + "|" + risk; }

    /**
     * Whether the committee route authorised this change.
     *
     * <p>Asked through the port so the engine keeps the single answer to "was this quorate?" — the
     * same reason {@code ChangeManagementService} asks it rather than counting resolution votes.
     *
     * <p><b>Fails towards reporting, not towards silence.</b> With no engine wired, or no referral,
     * this returns {@code false} and the vote-based checks below decide the finding. That is the
     * correct direction: a reconciliation that assumed committee authority it could not see would
     * report a clean platform on the strength of a missing bean — which is F2-95 verbatim, and is
     * the opposite of the fail-open the register's *reads* are allowed.
     */
    private boolean committeeAuthorised(ChangeRequest c, int min) {
        if (committeePort == null) return false;
        try {
            if (committeePort.activeReferral("CHANGE", c.getId()).isEmpty()) return false;
            return committeePort.decisionApproves("CHANGE", c.getId(), min);
        } catch (RuntimeException engineUnavailable) {
            // Logged rather than swallowed: "the register could not consult the committee engine" is
            // an operational fact, and the findings it then reports are vote-based only.
            log.warn("CE-11: committee engine unavailable while reconciling change {} — the register "
                   + "is reporting on recorded votes alone for this change: {}",
                     c.getId(), engineUnavailable.getMessage());
            return false;
        }
    }

    private UnauthorisedChangeFinding finding(ChangeRequest c, String reason, boolean effective,
                                              String gate, Integer min, int approvals, boolean stamped) {
        // UNATTRIBUTED stays MEDIUM even once effective: the votes are on record, so the gap is in the
        // platform's bookkeeping and not in the authority itself.
        String severity = UnauthorisedChangeFinding.UNATTRIBUTED_AUTHORISATION.equals(reason)
            ? UnauthorisedChangeFinding.MEDIUM
            : (effective ? UnauthorisedChangeFinding.HIGH : UnauthorisedChangeFinding.MEDIUM);
        return new UnauthorisedChangeFinding(c.getId(), c.getChangeCode(), c.getTitleEn(), c.getTitleAr(),
            c.getChangeType(), c.getRiskLevel(), c.getStatus(), reason, severity, effective,
            gate, min, approvals, stamped, c.getRatificationState(), null, null, null, null);
    }

    private Map<Long, Long> approveCountsFor(List<ChangeRequest> population) {
        if (population.isEmpty()) return Map.of();
        List<Long> ids = population.stream().map(ChangeRequest::getId).toList();
        Map<Long, Long> out = new HashMap<>();
        for (Object[] row : approvalRepo.countApprovalsByChange(ids))
            out.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        return out;
    }

    private Map<String, UnauthorisedChangeDisposition> dispositionsByKey(Long org) {
        if (dispositionRepo == null) return Map.of();
        Map<String, UnauthorisedChangeDisposition> out = new HashMap<>();
        for (UnauthorisedChangeDisposition d : dispositionRepo.findByOrganisationId(org))
            out.put(d.getChangeRequestId() + ":" + d.getReasonCode(), d);
        return out;
    }

    private Map<Long, ChangeModelUsage> modelUsagesFor(Long org) {
        if (usageRepo == null) return Map.of();
        Map<Long, ChangeModelUsage> out = new HashMap<>();
        for (ChangeModelUsage u : usageRepo.findByOrganisationId(org)) out.put(u.getChangeRequestId(), u);
        return out;
    }

    /**
     * Whether the model backing a usage was a live pre-approval.
     *
     * <p>⚠ Evaluated against <b>now</b>, not against {@code appliedAt}, and that is the honest choice
     * rather than the convenient one: {@link ChangeModel} carries a single authorisation window, so
     * asking whether it was live at some past instant would mean reconstructing history the row does
     * not hold. Judging by the current window can only ever flag a model whose authority has lapsed,
     * which is a real finding an operator can act on. Fails <b>closed</b> when the model row is gone —
     * a usage pointing at nothing is not evidence of authority.
     */
    private boolean modelWasLiveAt(ChangeModelUsage usage, LocalDateTime at) {
        if (modelRepo == null || usage.getChangeModelId() == null) return false;
        Optional<ChangeModel> m = modelRepo.findById(usage.getChangeModelId());
        return m.isPresent() && m.get().isLivePreApproval(at);
    }
}
