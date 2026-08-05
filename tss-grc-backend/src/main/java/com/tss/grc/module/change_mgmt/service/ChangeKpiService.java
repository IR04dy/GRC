package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.repository.ChangeConditionRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S8b / CE-12 — the ITIL/ISO KPI pack (G-12).
 *
 * <p>§12 of the uplift plan lists twenty metrics. <b>Measured 2026-08-04 in both databases, seven of
 * them have no source at all</b>, five are computable only over a denominator far smaller than the
 * population they appear to describe, and one — the CAB quorum-met rate — is complete but rests
 * entirely on values a human typed. So the deliverable is not twenty percentages; it is twenty
 * {@link ChangeKpi} rows each carrying what it was computed over and whether it was measured or
 * asserted.
 *
 * <pre>
 *   change_requests, dev ........ 17 live rows
 *     outcome set on ............ 2   -> success / failed / rollback rates cover 2 of 17
 *     cab_decision_at ........... 2   -> lead time to decision has a sample of 2
 *     implementation_started .... 0   -> lead time to implementation: NOT MEASURABLE
 *     implementation_ended ...... 0   -> schedule adherence:          NOT MEASURABLE
 *     pir_reviewed_at ........... 0   -> PIR completion:               NOT MEASURABLE (see F2-105)
 *     submitted_at .............. column does not exist
 *   CAB + ECAB committees ....... 4 of 34 meetings; 2 held, 2 scheduled
 *     quorum basis recorded ..... 2   -> rate covers 2 of 2 held
 *     of which ASSERTED ......... 2   -> provenance = ASSERTED, not MEASURED
 *     attendance rows ........... 0 in dev, 0 in prod -> attendance rate: NOT MEASURABLE
 *     member terms .............. 0   -> nothing can expire (and F2-96 is why)
 * </pre>
 *
 * <p><b>What this class refuses to do.</b> Report {@code 0%} for a metric with no data; omit a metric
 * with no source; or print a percentage without its denominator. All three are the same mistake in
 * different clothes — a KPI page that looks complete because the gaps are invisible — and the module
 * already shipped one instance of it (F2-107: {@code successRatePct} renders <b>100%</b> in dev off two
 * rows out of seventeen).
 */
@Slf4j
@Service
public class ChangeKpiService {

    // ── metric keys. Stable: they are i18n key suffixes and probe assertions. ────────────────────

    public static final String EMERGENCY_RATIO        = "emergencyRatio";
    public static final String SUCCESS_RATE           = "successRate";
    public static final String FAILED_CHANGE_RATE     = "failedChangeRate";
    public static final String ROLLBACK_RATE          = "rollbackRate";
    public static final String UNAUTHORISED_COUNT     = "unauthorisedChangeCount";
    public static final String LEAD_TIME_TO_DECISION  = "leadTimeSubmitToDecisionDays";
    public static final String LEAD_TIME_TO_IMPLEMENT = "leadTimeDecisionToImplementedDays";
    public static final String SCHEDULE_ADHERENCE     = "scheduleAdherenceRate";
    public static final String PIR_COMPLETION         = "pirCompletionRate";
    public static final String CONDITIONS_OVERDUE     = "openConditionsOverdue";

    public static final String CAB_MEETINGS_HELD      = "cabMeetingsHeld";
    public static final String CAB_MEETING_HELD_RATE  = "cabMeetingsHeldRate";
    public static final String CAB_QUORUM_MET_RATE    = "cabQuorumMetRate";
    public static final String CAB_ITEMS_PER_MEETING  = "cabItemsDecidedPerMeeting";
    public static final String CAB_DEFERRAL_RATE      = "cabDeferralRate";
    public static final String CAB_TIME_IN_QUEUE      = "cabTimeInQueueDays";
    public static final String CAB_ATTENDANCE_RATE    = "cabMemberAttendanceRate";
    public static final String CAB_NOTICE_COMPLIANCE  = "cabAgendaNoticeCompliance";
    public static final String CAB_TERMS_EXPIRING     = "cabTermsExpiring";

    /** Every key §12 asks for, so a test can prove none was quietly dropped. */
    public static final List<String> ALL_KEYS = List.of(
        EMERGENCY_RATIO, SUCCESS_RATE, FAILED_CHANGE_RATE, ROLLBACK_RATE, UNAUTHORISED_COUNT,
        LEAD_TIME_TO_DECISION, LEAD_TIME_TO_IMPLEMENT, SCHEDULE_ADHERENCE, PIR_COMPLETION,
        CONDITIONS_OVERDUE,
        CAB_MEETINGS_HELD, CAB_MEETING_HELD_RATE, CAB_QUORUM_MET_RATE, CAB_ITEMS_PER_MEETING,
        CAB_DEFERRAL_RATE, CAB_TIME_IN_QUEUE, CAB_ATTENDANCE_RATE, CAB_NOTICE_COMPLIANCE,
        CAB_TERMS_EXPIRING);

    /** Statuses meaning the change reached an end and its outcome should have been recorded. */
    static final List<String> TERMINAL_STATUSES = List.of("IMPLEMENTED", "ROLLED_BACK", "CLOSED");

    private final ChangeRequestRepository changeRepo;
    private final ChangeConditionRepository conditionRepo;
    private final ChangeGovernanceHealthService health;
    /** S8f/CE-11. Optional so a unit test can build the pack without the reconciliation wired. */
    private final UnauthorisedChangeRegisterService unauthorisedRegister;
    private final EntityManager em;

    public ChangeKpiService(ChangeRequestRepository changeRepo,
                            @Autowired(required = false) ChangeConditionRepository conditionRepo,
                            @Autowired(required = false) ChangeGovernanceHealthService health,
                            @Autowired(required = false) UnauthorisedChangeRegisterService unauthorisedRegister,
                            EntityManager em) {
        this.changeRepo = changeRepo;
        this.conditionRepo = conditionRepo;
        this.health = health;
        this.unauthorisedRegister = unauthorisedRegister;
        this.em = em;
    }

    /**
     * The whole pack, keyed by metric. Insertion-ordered so the UI renders change metrics before CAB
     * metrics without needing its own ordering table.
     */
    @Transactional(readOnly = true)
    public Map<String, ChangeKpi> kpis() {
        Map<String, ChangeKpi> out = new LinkedHashMap<>();
        for (ChangeKpi k : changeKpis()) out.put(k.key(), k);
        for (ChangeKpi k : cabKpis()) out.put(k.key(), k);
        return out;
    }

    // ── change-side ─────────────────────────────────────────────────────────────────────────────

    List<ChangeKpi> changeKpis() {
        Long org = TenantContext.getCurrentTenant();
        List<ChangeKpi> out = new ArrayList<>();

        long total = changeRepo.countByOrganisationId(org);

        // Emergency ratio — the one headline ITIL metric that is fully measurable here, because
        // change_type is NOT NULL on every row. 1 of 17 in dev.
        out.add(ChangeKpi.pct(EMERGENCY_RATIO,
            changeRepo.countByOrganisationIdAndChangeType(org, "EMERGENCY"), total));

        // Outcome-based rates. ⚠ The denominator is changes that REACHED AN END, not changes that
        // happen to have an outcome recorded — that substitution is F2-107. A change sitting at
        // IMPLEMENTED with no outcome is a missing record, and dividing it away hides it.
        long reachedEnd = 0;
        for (String s : TERMINAL_STATUSES) reachedEnd += changeRepo.countByOrganisationIdAndStatus(org, s);
        long success  = changeRepo.countByOrganisationIdAndOutcome(org, "SUCCESS");
        long failed   = changeRepo.countByOrganisationIdAndOutcome(org, "FAILED");
        long rolled   = changeRepo.countByOrganisationIdAndOutcome(org, "ROLLED_BACK");

        out.add(ChangeKpi.pctOfSubset(SUCCESS_RATE,       success, reachedEnd, reachedEnd));
        out.add(ChangeKpi.pctOfSubset(FAILED_CHANGE_RATE, failed,  reachedEnd, reachedEnd));
        out.add(ChangeKpi.pctOfSubset(ROLLBACK_RATE,      rolled,  reachedEnd, reachedEnd));

        // S8f/CE-11 — this was the pack's NOT_MEASURABLE entry until the register existed, and it is
        // now computed. ⚠ Three things about the number:
        //   * it counts CHANGES, not findings — one change failing for two reasons is one unauthorised
        //     change, and counting findings would inflate a compliance figure;
        //   * it counts only genuine AUTHORITY GAPS. Six of dev's eleven findings are
        //     UNATTRIBUTED_AUTHORISATION (the votes are on record; the decision stamp is not), and
        //     folding those in would report eight where three is the truth — more than double;
        //   * it counts DISPOSED gaps too, so the figure cannot be cleared by signing findings off.
        // The population is changes past their authorisation point, not all changes: dividing by rows
        // that were never due for authorisation is F2-107 in a new denominator.
        if (unauthorisedRegister != null)
            out.add(ChangeKpi.count(UNAUTHORISED_COUNT,
                unauthorisedRegister.authorityGapChangeCount(), unauthorisedRegister.populationSize()));
        else
            out.add(ChangeKpi.notMeasurable(UNAUTHORISED_COUNT, ChangeKpi.COUNT,
                ChangeKpi.Blocked.CE11_REGISTER_UNAVAILABLE));

        // Lead time to CAB decision. There is no submitted_at column — created_at is the record's
        // birth, not its submission — so this is reported against what exists and named for it.
        out.add(leadTimeToDecision(org, total));

        out.add(ChangeKpi.notMeasurable(LEAD_TIME_TO_IMPLEMENT, ChangeKpi.DAYS,
            ChangeKpi.Blocked.NO_IMPLEMENTATION_TIMESTAMPS));
        out.add(ChangeKpi.notMeasurable(SCHEDULE_ADHERENCE, ChangeKpi.PCT,
            ChangeKpi.Blocked.NO_IMPLEMENTATION_TIMESTAMPS));
        out.add(ChangeKpi.notMeasurable(PIR_COMPLETION, ChangeKpi.PCT,
            ChangeKpi.Blocked.NO_PIR_TIMESTAMPS));

        // S7e's register, two slices old. ⚠ The population is CONDITIONS, not changes — counting
        // overdue conditions "out of 17 changes" mixes two units and was wrong in my own first draft,
        // which the live probe showed as "0/17". With no conditions on record the honest answer is
        // NO_DATA: "nothing is overdue" and "nobody has imposed a condition yet" are different facts.
        long conditionsTotal = conditionRepo == null ? 0 : conditionRepo.countByOrganisationId(org);
        long overdue = conditionRepo == null ? 0
            : conditionRepo.countOverdueOpen(org, LocalDateTime.now());
        out.add(conditionsTotal > 0
            ? ChangeKpi.count(CONDITIONS_OVERDUE, overdue, conditionsTotal)
            : ChangeKpi.noData(CONDITIONS_OVERDUE, ChangeKpi.COUNT, 0L));

        return out;
    }

    /**
     * Median days from record creation to CAB decision, over changes that have both.
     *
     * <p>⚠ <b>Median, not mean, and the sample size travels with it.</b> With 2 decided changes in dev
     * a mean is one outlier away from meaningless; reporting the sample size is what lets a reader see
     * that. Named {@code submitToDecision} in §12 but computed from {@code created_at}, because no
     * {@code submitted_at} column exists — {@link ChangeKpi.Blocked#NO_SUBMISSION_TIMESTAMP} records
     * that substitution rather than letting the key imply a precision the data has not got.
     */
    private ChangeKpi leadTimeToDecision(Long org, long population) {
        @SuppressWarnings("unchecked")
        List<Number> days = em.createNativeQuery("""
            SELECT DATEDIFF(DAY, c.created_at, c.cab_decision_at)
              FROM change_mgmt.change_requests c
             WHERE c.organisation_id = ?1 AND c.is_deleted = 0
               AND c.cab_decision_at IS NOT NULL AND c.created_at IS NOT NULL
             ORDER BY 1
            """).setParameter(1, org).getResultList();
        if (days.isEmpty()) return ChangeKpi.noData(LEAD_TIME_TO_DECISION, ChangeKpi.DAYS, population);
        List<Double> sorted = days.stream().filter(java.util.Objects::nonNull)
            .map(Number::doubleValue).sorted().toList();
        if (sorted.isEmpty()) return ChangeKpi.noData(LEAD_TIME_TO_DECISION, ChangeKpi.DAYS, population);
        int m = sorted.size() / 2;
        double median = sorted.size() % 2 == 1 ? sorted.get(m) : (sorted.get(m - 1) + sorted.get(m)) / 2.0;
        return ChangeKpi.days(LEAD_TIME_TO_DECISION, median, sorted.size(), population);
    }

    // ── CAB side ────────────────────────────────────────────────────────────────────────────────

    List<ChangeKpi> cabKpis() {
        List<ChangeKpi> out = new ArrayList<>();

        if (health == null) {
            for (String key : List.of(CAB_MEETINGS_HELD, CAB_MEETING_HELD_RATE, CAB_QUORUM_MET_RATE,
                                      CAB_ITEMS_PER_MEETING, CAB_DEFERRAL_RATE, CAB_TIME_IN_QUEUE,
                                      CAB_ATTENDANCE_RATE, CAB_NOTICE_COMPLIANCE, CAB_TERMS_EXPIRING))
                out.add(ChangeKpi.notMeasurable(key, ChangeKpi.COUNT,
                    ChangeKpi.Blocked.COMMITTEE_ENGINE_UNAVAILABLE));
            return out;
        }

        ChangeGovernanceHealthService.CabThroughput t = health.cabThroughput();

        // A17-D2's unconfigured tenant. "No CAB is mapped" must not render as CAB performance —
        // "0 meetings held" would read as a body that never met rather than a body that never existed.
        if (t.isUnconfigured()) {
            for (String key : List.of(CAB_MEETINGS_HELD, CAB_MEETING_HELD_RATE, CAB_QUORUM_MET_RATE,
                                      CAB_ITEMS_PER_MEETING, CAB_DEFERRAL_RATE, CAB_TIME_IN_QUEUE,
                                      CAB_ATTENDANCE_RATE, CAB_NOTICE_COMPLIANCE, CAB_TERMS_EXPIRING))
                out.add(ChangeKpi.notMeasurable(key, ChangeKpi.COUNT,
                    ChangeKpi.Blocked.COMMITTEE_ENGINE_UNAVAILABLE));
            return out;
        }

        out.add(ChangeKpi.count(CAB_MEETINGS_HELD, t.meetingsHeld(), t.meetingsTotal()));
        out.add(ChangeKpi.pct(CAB_MEETING_HELD_RATE, t.meetingsHeld(), t.meetingsTotal()));

        // ⚠ The slice's sharpest figure. Coverage is complete (2 of 2 held meetings recorded a basis)
        // so `basis` is COMPUTED — but both bases are ASSERTED_LEGACY, so the rate is 100% of a claim
        // nobody computed. Without the provenance axis this renders as a passed control.
        ChangeKpi quorum = ChangeKpi.pctOfSubset(CAB_QUORUM_MET_RATE,
            t.quorumMet(), t.quorumBasisRecorded(), t.meetingsHeld());
        out.add(quorum.withProvenance(provenanceOf(t.quorumBasisRecorded(), t.quorumBasisAsserted())));

        out.add(ChangeKpi.perMeeting(CAB_ITEMS_PER_MEETING, t.resolutionsDecided(), t.meetingsHeld()));
        out.add(ChangeKpi.pctOfSubset(CAB_DEFERRAL_RATE,
            t.resolutionsDeferred(), t.resolutions(), t.resolutions()));

        // No referral has ever reached DECIDED, so time-in-queue has no closing bracket. Not a zero:
        // "decisions are instant" is the opposite of the truth.
        out.add(t.decidedReferrals() > 0
            ? ChangeKpi.days(CAB_TIME_IN_QUEUE, 0, t.decidedReferrals(), t.decidedReferrals())
            : ChangeKpi.notMeasurable(CAB_TIME_IN_QUEUE, ChangeKpi.DAYS,
                                      ChangeKpi.Blocked.NO_DECIDED_REFERRALS));

        out.add(t.attendanceRowsRecorded() > 0
            ? ChangeKpi.pctOfSubset(CAB_ATTENDANCE_RATE, t.attendanceRowsRecorded(),
                                    t.attendanceRowsRecorded(), t.meetingsHeld())
            : ChangeKpi.notMeasurable(CAB_ATTENDANCE_RATE, ChangeKpi.PCT,
                                      ChangeKpi.Blocked.NO_ATTENDANCE_RECORDS));

        // S7b measured this and pinned it: CommitteeMeeting has no publication act at all, so notice
        // compliance has nothing to measure against. Refuted rather than left as a to-do.
        out.add(ChangeKpi.notMeasurable(CAB_NOTICE_COMPLIANCE, ChangeKpi.PCT,
            ChangeKpi.Blocked.NO_PUBLICATION_ACT));

        // ⚠ 0 member terms on the gate-mapped committees, in BOTH databases — F2-96 restated as a KPI.
        // My first draft reported "0 expiring, COMPUTED" over a population of 0, which is trivially
        // true and reads as a clean bill of health for a roster that does not exist. NO_DATA is the
        // honest state: nothing can expire because nobody has been appointed.
        out.add(t.memberTerms() > 0
            ? ChangeKpi.count(CAB_TERMS_EXPIRING, t.termsExpiringSoon(), t.memberTerms())
            : ChangeKpi.noData(CAB_TERMS_EXPIRING, ChangeKpi.COUNT, 0L));

        return out;
    }

    /** {@code MEASURED} / {@code ASSERTED} / {@code MIXED}, from how many bases were asserted. */
    static String provenanceOf(long recorded, long asserted) {
        if (recorded <= 0 || asserted <= 0) return ChangeKpi.MEASURED;
        if (asserted >= recorded) return ChangeKpi.ASSERTED;
        return ChangeKpi.MIXED;
    }
}
