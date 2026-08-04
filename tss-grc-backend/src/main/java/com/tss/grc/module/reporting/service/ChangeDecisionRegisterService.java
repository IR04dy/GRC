package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import com.tss.grc.module.admin.committee.entity.CommitteeResolution;
import com.tss.grc.module.admin.committee.entity.CommitteeResolutionVote;
import com.tss.grc.module.admin.committee.repository.CommitteeReferralRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeResolutionVoteRepository;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeConditionRepository;
import com.tss.grc.module.change_mgmt.service.ChangeConditionStatus;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * S8d / CE-14b — the CAB decision register.
 *
 * <h3>What the measurement changed</h3>
 * <p>§9.3 of the uplift plan sources this from {@code change_decision} and
 * {@code change_decision_condition}. <b>Neither table exists</b> — S7c wrote the decision back as
 * <em>columns</em> on {@code change_requests} and S7e hung conditions off the change — so the sources
 * had to be measured before a row shape could be chosen. Measured 2026-08-04:
 *
 * <table><caption>decision evidence, per database</caption>
 *   <tr><th></th><th>dev</th><th>deployed</th></tr>
 *   <tr><td>changes past a decision point</td><td>11</td><td>10</td></tr>
 *   <tr><td>…carrying a decision <b>timestamp</b></td><td><b>2</b></td><td><b>1</b></td></tr>
 *   <tr><td>…carrying a decision <b>author</b></td><td><b>1</b></td><td>column absent (V345+)</td></tr>
 *   <tr><td>legacy per-change votes</td><td>31 across 12 changes</td><td>31 across 12</td></tr>
 *   <tr><td>engine resolution votes</td><td>4 (3 FOR, 1 RECUSED)</td><td>0</td></tr>
 * </table>
 *
 * <p>So a register printing one row per <em>stamped</em> decision would show <b>one</b> decision while
 * the platform holds eleven changes that have demonstrably passed a CAB gate and thirty-one recorded
 * votes. A register printing all eleven as "decided by … on …" would <b>invent nine decisions</b>.
 * Both are wrong, which is why every row carries two independent axes — the same shape S7f needed for
 * the minutes and S8b for the KPI pack, arrived at for a third time from a third measurement:
 *
 * <ul>
 *   <li>{@link #BASIS_RECORDED} vs {@link #BASIS_INFERRED} — <b>is the decision itself recorded?</b>
 *       A change in {@code IMPLEMENTING} cannot have got there without passing an approval, so the
 *       state <em>proves</em> a decision happened; it does not say when or by whom. That is an
 *       inference, and it is labelled as one.</li>
 *   <li>{@link #EVIDENCE_RESOLUTION} / {@link #EVIDENCE_VOTES} / {@link #EVIDENCE_NONE} — <b>what
 *       backs it?</b> A carried committee resolution (the system of record), a legacy per-change vote
 *       tally with named approvers, or nothing at all.</li>
 * </ul>
 *
 * <p>The axes are genuinely independent in the live data: change 213 is {@code RECORDED} +
 * {@code RESOLUTION}, changes 1–12 are {@code INFERRED} + {@code VOTES}, and a change approved
 * through the {@code AUTO} gate is {@code INFERRED} + {@code NONE} — correctly, because no committee
 * was ever asked. Collapsing them would print "decided" against rows nothing decided.
 *
 * <h3>Why this lives in {@code module/reporting} and not in {@code change_mgmt}</h3>
 * <p>The register joins change rows to committee referrals, resolutions and votes. Built inside
 * {@code change_mgmt} it would need either a new {@code CommitteePort} method or more native SQL
 * across the boundary (F2-108). Reporting already imports both sides — S7f put the committee
 * generators here for the same reason — so the read belongs here and the module boundary stays a
 * boundary.
 */
@Service
@RequiredArgsConstructor
public class ChangeDecisionRegisterService {

    /** The change carries a decision timestamp: somebody recorded the act. */
    public static final String BASIS_RECORDED = "RECORDED";
    /** The state proves an approval happened; nothing recorded when or by whom. */
    public static final String BASIS_INFERRED = "INFERRED_FROM_STATE";

    /** A carried committee resolution — the system of record for a CAB decision. */
    public static final String EVIDENCE_RESOLUTION = "RESOLUTION";
    /** Legacy per-change votes with named approvers ({@code change_approvals}). */
    public static final String EVIDENCE_VOTES = "RECORDED_VOTES";
    /** Nothing backs it. Correct and expected for an {@code AUTO}-gated change. */
    public static final String EVIDENCE_NONE = "NONE";

    /**
     * States that can only be reached by passing a decision point. Deliberately excludes
     * {@code CAB_PENDING} (the committee has not answered yet) and {@code DRAFT} /
     * {@code SUBMITTED} / {@code IMPACT_ASSESSED} / {@code CANCELLED}.
     *
     * <p>⚠ {@code CAB_DEFERRED} and {@code MORE_INFO_REQUIRED} <b>are</b> decisions — S7c exists
     * because a committee that defers had nowhere to say so, and a register that dropped them would
     * re-open exactly that hole from the other end.
     */
    static final Set<String> DECIDED_STATES = Set.of(
        "CAB_APPROVED", "CAB_REJECTED", "CAB_DEFERRED", "MORE_INFO_REQUIRED", "EMERGENCY_APPROVED",
        "SCHEDULED", "IMPLEMENTING", "IMPLEMENTED", "POST_REVIEW", "ROLLED_BACK", "CLOSED");

    /** status → the decision it evidences. Downstream states evidence an approval, not their own name. */
    private static final Map<String, String> DECISION_OF = Map.ofEntries(
        Map.entry("CAB_APPROVED", "APPROVED"),
        Map.entry("CAB_REJECTED", "REJECTED"),
        Map.entry("CAB_DEFERRED", "DEFERRED"),
        Map.entry("MORE_INFO_REQUIRED", "MORE_INFO_REQUIRED"),
        Map.entry("EMERGENCY_APPROVED", "EMERGENCY_APPROVED"),
        Map.entry("SCHEDULED", "APPROVED"),
        Map.entry("IMPLEMENTING", "APPROVED"),
        Map.entry("IMPLEMENTED", "APPROVED"),
        Map.entry("POST_REVIEW", "APPROVED"),
        Map.entry("ROLLED_BACK", "APPROVED"),
        Map.entry("CLOSED", "APPROVED"));

    private final ChangeRequestRepository changeRepo;
    private final ChangeApprovalRepository approvalRepo;
    private final CommitteeReferralRepository referralRepo;
    private final CommitteeResolutionRepository resolutionRepo;
    private final CommitteeResolutionVoteRepository voteRepo;

    /** Optional: absent until V352 is applied, and the register must degrade rather than 500. */
    @Autowired(required = false)
    private ChangeConditionRepository conditionRepo;

    /**
     * One decided change. {@code decidedAt} and {@code decidedBy} are <b>nullable on purpose</b> —
     * a renderer must print "not recorded", never a blank that reads as "nobody objected" and never
     * a substituted timestamp.
     */
    public record DecisionRow(
        Long changeId, String changeCode, String titleEn, String titleAr,
        String changeType, String riskLevel, String status,
        String decision, String basis, String evidence,
        LocalDateTime decidedAt, String decidedBy, String decisionNotes,
        String resolutionCode, String resolutionOutcome, String tallySource,
        Integer voteFor, Integer voteAgainst, Integer voteAbstain,
        List<String> recusals, String voteSummary,
        long conditionsTotal, long conditionsOpen) {

        /** True when the register can name a decision act. */
        public boolean isAttributed() { return decidedAt != null || decidedBy != null; }

        /**
         * How a reader refers to this change. ⚠ <b>F2-109</b>: nothing in the platform generates
         * {@code change_code} — the single {@code setChangeCode} call in {@code module/} is a
         * merge of a client-supplied value — so every change created through
         * {@code POST /api/v1/changes} carries NULL, and a register keyed on it would print blank
         * identifiers. Both databases look clean only because their rows were seeded with codes.
         * Until a generator exists, fall back to the platform's real identifier rather than an
         * empty cell: a row an auditor cannot cite is not a record.
         */
        public String reference() {
            return changeCode != null && !changeCode.isBlank() ? changeCode : "#" + changeId;
        }
    }

    /**
     * The register, plus the counts a reader needs to judge it. Never a bare list: "12 decisions" with
     * no statement of how many are attributed is the F2-107 shape in table form.
     */
    public record Register(List<DecisionRow> rows, long total, long attributed,
                           Map<String, Long> byDecision, Map<String, Long> byBasis,
                           Map<String, Long> byEvidence) {}

    @Transactional(readOnly = true)
    public Register build() {
        Long org = TenantContext.getCurrentTenant();
        List<ChangeRequest> all = changeRepo
            .findByOrganisationIdOrderByCreatedAtDesc(org, PageRequest.of(0, 1000)).getContent();

        List<DecisionRow> rows = new ArrayList<>();
        for (ChangeRequest c : all) {
            if (c.getStatus() == null || !DECIDED_STATES.contains(c.getStatus())) continue;
            rows.add(row(org, c));
        }
        // Most recently decided first; unattributed rows sort last rather than first, because a
        // register whose top rows are all blank looks broken and gets closed.
        rows.sort(Comparator
            .comparing((DecisionRow r) -> r.decidedAt() == null)
            .thenComparing(DecisionRow::decidedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(r -> r.changeCode() == null ? "" : r.changeCode()));

        return new Register(rows, rows.size(),
            rows.stream().filter(DecisionRow::isAttributed).count(),
            tally(rows, DecisionRow::decision), tally(rows, DecisionRow::basis),
            tally(rows, DecisionRow::evidence));
    }

    private DecisionRow row(Long org, ChangeRequest c) {
        String basis = c.getCabDecisionAt() != null || c.getCabDecisionByName() != null
            ? BASIS_RECORDED : BASIS_INFERRED;

        // Evidence, strongest first. A carried resolution outranks a legacy tally: the resolution is
        // what the committee actually passed, the tally is what somebody typed against the change.
        String evidence = EVIDENCE_NONE;
        String resolutionCode = null, resolutionOutcome = null, tallySource = null;
        Integer vFor = null, vAgainst = null, vAbstain = null;
        List<String> recusals = new ArrayList<>();

        Optional<CommitteeReferral> referral =
            referralRepo.findByOrganisationIdAndSourceModuleAndSourceEntityId(org, "CHANGE", c.getId());
        if (referral.isPresent() && referral.get().getResolutionId() != null) {
            Optional<CommitteeResolution> res =
                resolutionRepo.findByIdAndOrganisationId(referral.get().getResolutionId(), org);
            if (res.isPresent()) {
                CommitteeResolution r = res.get();
                evidence = EVIDENCE_RESOLUTION;
                resolutionCode = r.getResolutionCode();
                resolutionOutcome = r.getOutcome();
                tallySource = r.getTallySource();
                vFor = r.getVoteFor();
                vAgainst = r.getVoteAgainst();
                vAbstain = r.getVoteAbstain();
                for (CommitteeResolutionVote v : voteRepo.findByResolutionId(r.getId())) {
                    if ("RECUSED".equalsIgnoreCase(v.getVote()) && v.getMemberName() != null) {
                        recusals.add(v.getMemberName());
                    }
                }
            }
        }

        List<ChangeApproval> votes = approvalRepo.findByChangeRequestIdOrderByDecidedAtAsc(c.getId());
        if (evidence.equals(EVIDENCE_NONE) && !votes.isEmpty()) evidence = EVIDENCE_VOTES;

        long condTotal = 0, condOpen = 0;
        if (conditionRepo != null) {
            condTotal = conditionRepo.countByOrganisationIdAndChangeRequestId(org, c.getId());
            condOpen = conditionRepo.countByOrganisationIdAndChangeRequestIdAndStatus(
                org, c.getId(), ChangeConditionStatus.OPEN);
        }

        return new DecisionRow(
            c.getId(), c.getChangeCode(), c.getTitleEn(), c.getTitleAr(),
            c.getChangeType(), c.getRiskLevel(), c.getStatus(),
            DECISION_OF.getOrDefault(c.getStatus(), c.getStatus()), basis, evidence,
            c.getCabDecisionAt(), c.getCabDecisionByName(), c.getCabDecisionNotes(),
            resolutionCode, resolutionOutcome, tallySource, vFor, vAgainst, vAbstain,
            recusals, summarise(votes), condTotal, condOpen);
    }

    /** "APPROVE 3, DEFER 1" — the tally, never an interpretation of it. Empty string when none. */
    static String summarise(List<ChangeApproval> votes) {
        if (votes == null || votes.isEmpty()) return "";
        Map<String, Integer> counts = new TreeMap<>();
        for (ChangeApproval v : votes) {
            String d = v.getDecision() == null ? "UNKNOWN" : v.getDecision().toUpperCase();
            counts.merge(d, 1, Integer::sum);
        }
        StringBuilder sb = new StringBuilder();
        counts.forEach((k, n) -> sb.append(sb.length() == 0 ? "" : ", ").append(k).append(' ').append(n));
        return sb.toString();
    }

    private static Map<String, Long> tally(List<DecisionRow> rows,
                                           java.util.function.Function<DecisionRow, String> key) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (DecisionRow r : rows) out.merge(key.apply(r), 1L, Long::sum);
        return out;
    }
}
