package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.module.change_mgmt.entity.UnauthorisedChangeDisposition;

import java.time.LocalDateTime;
import java.util.List;

/**
 * S8f / CE-11 — one reconciliation failure between the authorisation a change <b>required</b> and the
 * authorisation the platform can <b>show</b> it received.
 *
 * <p>G-11, severity High: <i>"Unauthorised changes undetectable — no reconciliation of implemented vs
 * authorised."</i> Three sides are needed to reconcile and all three already existed:
 *
 * <pre>
 *   REQUIRED  change_authorisation_matrix  the cell for (change_type x risk_level): gate + min_approvals
 *   RECORDED  change_approvals             the votes actually cast, plus cab_decision_at as attribution
 *   REACHED   change_requests.status       whether the change passed its authorisation point
 * </pre>
 *
 * <p><b>Findings are derived, never stored.</b> A stored verdict can disagree with the rows it claims
 * to describe — the reason S7e refused to persist a {@code CONDITIONAL} literal and computed
 * conditionality instead. Every finding here is a pure function of the three sides above, so it cannot
 * drift from them. What <em>is</em> stored is the {@link UnauthorisedChangeDisposition}: a human's
 * decision about a finding is an act, and an act cannot be recomputed.
 *
 * <h3>⚠ Two different facts, and merging them would have been the defect</h3>
 * The measurement that shaped this record: of the eleven dev changes past their authorisation point,
 * <b>six</b> satisfy their cell's {@code min_approvals} in full and still carry no
 * {@code cab_decision_at}. Reporting those six as "unauthorised" would be false — the votes are on
 * record — and dropping them would hide that the platform cannot say who decided or when. So
 * {@link #UNATTRIBUTED_AUTHORISATION} is its own reason code at {@link #MEDIUM}, excluded from
 * {@link #isAuthorityGap()}, and the KPI counts only genuine authority gaps. That is S8d's
 * basis-versus-evidence split and S8b's coverage-versus-provenance split, arriving a third time
 * because it is the same underlying distinction: <i>was authority present</i> and <i>can we prove who
 * exercised it</i> are not one question.
 *
 * <h3>What this reconciliation cannot see, and says so</h3>
 * See {@link UnauthorisedChangeRegisterService#UNDETECTABLE} — a change made in production that was
 * never recorded in the platform is invisible here, and no amount of care with these three tables
 * changes that. It needs a configuration-drift feed, and this deployment has none: there is no
 * CMDB, no configuration-item baseline, and the {@code integrations} schema holds no tables. The
 * register declares that gap rather than letting a clean report imply coverage it hasn't got.
 *
 * @param changeId              the change this finding is about
 * @param changeCode            its citable reference, or {@code null} — ⚠ F2-109, nothing generates one
 * @param reasonCode            one of the closed set below
 * @param severity              {@link #HIGH} once the change has taken effect, else {@link #MEDIUM}
 * @param effective             whether the change is live — the difference between a breach and a catch
 * @param gateRequired          {@code AUTO} / {@code CAB} / {@code ECAB} from the cell, or {@code null}
 *                              when no cell exists (which is itself {@link #NO_AUTHORISATION_POLICY})
 * @param minApprovalsRequired  what the cell demanded
 * @param approvalsRecorded     how many {@code APPROVE} votes are on record
 * @param cabDecisionStamped    whether {@code cab_decision_at} attributes the decision
 * @param disposition           the investigator's recorded outcome, or {@code null} while open
 */
public record UnauthorisedChangeFinding(
    Long changeId,
    String changeCode,
    String titleEn,
    String titleAr,
    String changeType,
    String riskLevel,
    String status,
    String reasonCode,
    String severity,
    boolean effective,
    String gateRequired,
    Integer minApprovalsRequired,
    Integer approvalsRecorded,
    boolean cabDecisionStamped,
    String ratificationState,
    String dispositionState,
    String dispositionByName,
    LocalDateTime dispositionAt,
    String dispositionNote
) {

    // ── reason codes. Closed, and each one measured against real rows before it was added. ───────

    /**
     * The cell demanded approvals and <b>none</b> are on record.
     *
     * <p>Measured in dev: change 7 is {@code SCHEDULED} on a {@code CAB}/min-1 cell whose only
     * recorded vote is a {@code DEFER}, and change 229 reached {@code CAB_APPROVED} with no vote at
     * all. A {@code cab_decision_at} stamp does not rescue either — a stamp is an attribution, not a
     * vote, and treating it as one would let the status column authorise itself.
     */
    public static final String NO_AUTHORISATION_RECORD = "NO_AUTHORISATION_RECORD";

    /**
     * Votes exist but fall short of the cell's {@code min_approvals}.
     *
     * <p>Measured in dev: change 3 is an {@code EMERGENCY}/{@code CRITICAL} change sitting at
     * {@code IMPLEMENTED} on an {@code ECAB} cell requiring <b>2</b> approvals with <b>1</b> recorded.
     * A critical emergency change is live in production on half the authority its tenant's own matrix
     * demands, and before this register nothing in the platform said so.
     */
    public static final String INSUFFICIENT_APPROVALS = "INSUFFICIENT_APPROVALS";

    /**
     * The requirement is met, but the platform cannot say who decided or when.
     *
     * <p>Not an authority gap — see the class javadoc. This is F2-103's legacy: {@code stampDecisionAuthor}
     * was missing from the {@code CAB_APPROVED} branch until S7c, so historic rows carry votes and no
     * stamp. Excluded from {@link #isAuthorityGap()} so it can never inflate the unauthorised count.
     */
    public static final String UNATTRIBUTED_AUTHORISATION = "UNATTRIBUTED_AUTHORISATION";

    /**
     * No matrix cell defines what this change's type and risk require.
     *
     * <p>The fail-closed branch. It cannot fire on today's data — all three tenants carry a full
     * 12-cell matrix — but an unverifiable requirement must never read as a satisfied one, which is
     * the four-state lesson S6b-i took from the word "advisory".
     */
    public static final String NO_AUTHORISATION_POLICY = "NO_AUTHORISATION_POLICY";

    /**
     * A change was applied under a change model that was not a live pre-approval at the time.
     *
     * <p>S8e's other half. {@link ChangeModel#isLivePreApproval} is flag AND {@code APPROVED} AND
     * unexpired; a model that has since been retired, suspended or allowed to expire authorises
     * nothing. Yields nothing today — zero models exist in either database — and that is reported as
     * data, not hidden as absence.
     */
    public static final String EXPIRED_MODEL_APPLIED = "EXPIRED_MODEL_APPLIED";

    /**
     * An emergency change implemented on ECAB authority and never retrospectively ratified inside the
     * tenant's SLA.
     *
     * <p>⚠ Detected by A17-D3, <b>not recomputed here</b>. CE-11 was specified as an additional sink
     * rather than a replacement, so this reason mirrors {@code ratification_state} and the two
     * registers cannot disagree about the same change. Duplicating the predicate is how two registers
     * end up reporting different breach counts to the same auditor.
     */
    public static final String RATIFICATION_BREACHED = "RATIFICATION_BREACHED";

    public static final List<String> REASON_CODES = List.of(
        NO_AUTHORISATION_RECORD, INSUFFICIENT_APPROVALS, UNATTRIBUTED_AUTHORISATION,
        NO_AUTHORISATION_POLICY, EXPIRED_MODEL_APPLIED, RATIFICATION_BREACHED);

    /** Reason codes that mean authority was genuinely absent or short. The compliance-relevant set. */
    public static final List<String> AUTHORITY_GAP_REASONS = List.of(
        NO_AUTHORISATION_RECORD, INSUFFICIENT_APPROVALS,
        NO_AUTHORISATION_POLICY, EXPIRED_MODEL_APPLIED, RATIFICATION_BREACHED);

    public static final String HIGH   = "HIGH";
    public static final String MEDIUM = "MEDIUM";
    public static final List<String> SEVERITIES = List.of(HIGH, MEDIUM);

    /**
     * Whether authority was actually missing, as opposed to merely unattributed.
     *
     * <p>The KPI counts these. {@link #UNATTRIBUTED_AUTHORISATION} is deliberately not one: six of dev's
     * eleven findings are that code, and folding them in would report eight unauthorised changes where
     * three is the true number — overstating a compliance breach by more than double.
     */
    public boolean isAuthorityGap() { return AUTHORITY_GAP_REASONS.contains(reasonCode); }

    /** Open until an investigator records an outcome. A disposition annotates a finding; it never erases one. */
    public boolean isOpen() { return dispositionState == null; }

    /**
     * Stable identity for a finding, so a disposition can name one.
     *
     * <p>{@code changeId} alone is not enough — one change can fail reconciliation for two independent
     * reasons and each needs disposing on its own merits.
     */
    public String key() { return changeId + ":" + reasonCode; }

    /** Attach a stored disposition to a derived finding. */
    public UnauthorisedChangeFinding withDisposition(UnauthorisedChangeDisposition d) {
        if (d == null) return this;
        return new UnauthorisedChangeFinding(changeId, changeCode, titleEn, titleAr, changeType,
            riskLevel, status, reasonCode, severity, effective, gateRequired, minApprovalsRequired,
            approvalsRecorded, cabDecisionStamped, ratificationState,
            d.getState(), d.getDisposedByName(), d.getDisposedAt(), d.getNote());
    }
}
