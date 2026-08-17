package com.tss.grc.module.change_mgmt.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeAuthorisationMatrixRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.spi.CommitteePort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * CE-13 — the CAB queue: what this tenant's change authority has been asked to decide, and what is
 * waiting for it without having been asked.
 *
 * <h2>Why this is a queue and not a second committee screen</h2>
 * The CE plan's CE-13 row asks for a "CAB workspace (agenda builder, voting panel, minutes)". All
 * three already exist — in the <b>committee</b> workspace, generically, because under ADR-017 D-2 the
 * CAB <em>is</em> a committee: {@code admin.committee_gate_map} names which one per tenant (dev org 1:
 * CAB → committee 13 "IT Steering Committee", ECAB → committee 12 "Executive Committee"), and S6
 * measured {@code cab_profile}/{@code cab_member_role} to be 12-of-15 and 6-of-7 subsumed by the
 * generic engine and pinned them against being built. Rendering agendas, votes and minutes again on
 * the change side would be the same duplication S8f and S8g each refused, and {@link CommitteePort}'s
 * own javadoc forbids the port from carrying quorum, attendance, minutes or tallies for exactly that
 * reason. So this service composes and signposts; it never re-derives a decision.
 *
 * <h2>What was actually missing, measured 2026-08-05 in dev</h2>
 * <ul>
 *   <li>{@code CommitteeReferralService.awaitingDecision} — the CAB queue — is reachable over
 *       <b>no HTTP route at all</b>. It has one caller: the agenda builder.</li>
 *   <li>Referral is <b>manual</b>. Nothing in {@code ChangeManagementService.transition} refers a
 *       change on the way into {@code CAB_PENDING}; only the "Refer to committee" button does. So
 *       {@code CAB_PENDING} is a status the change wears, not a request anybody received — two dev
 *       changes sit there with no referral.</li>
 *   <li>The CAB's own next sitting ({@code MTG-IT-26Q3}, 2026-08-17) has a three-line agenda naming
 *       <b>no change</b>, while its only two open referrals point at change ids 101 and 102, which
 *       exist in <b>no</b> organisation.</li>
 * </ul>
 *
 * <h2>The three populations, kept apart</h2>
 * F2-94's distinction, third reuse: a name that is wrong is not a target that is empty, and neither is
 * a target nobody addressed. Each has a different remedy and merging them produces a number no one can
 * act on.
 * <ol>
 *   <li><b>Tabled</b> — a stated referral whose change exists. The committee has it. Nothing to do.</li>
 *   <li><b>Orphaned</b> — a referral that cannot be tabled: {@link Row#SUBJECT_NOT_STATED} (no V350
 *       snapshot, so the agenda builder excludes it by design) or {@link Row#SUBJECT_NOT_FOUND} (the
 *       change it refers to does not exist here). Remedy: clear the row.</li>
 *   <li><b>Unreferred</b> — a change at a CAB/ECAB gate whose decision is still ahead and which has no
 *       live referral. Remedy: refer it. This is the only one of the three the committee module
 *       <em>cannot</em> compute: S5a took its imports of {@code change_requests} to zero, so it cannot
 *       see the changes that ought to be in its own queue, and until now nothing on this side looked.</li>
 * </ol>
 */
@Service
public class ChangeCabQueueService {

    /**
     * Statuses where a CAB/ECAB decision is still ahead of the change.
     *
     * <p>{@code CAB_PENDING} is the queue proper. {@code CAB_DEFERRED} and {@code MORE_INFO_REQUIRED}
     * (S7c's dispositions) are in the set because both transition only back to {@code CAB_PENDING} —
     * the committee has not finished with them, it has asked for something first, and a workspace that
     * dropped them would report a smaller backlog than the CAB has.
     *
     * <p>{@code DRAFT}, {@code SUBMITTED} and {@code IMPACT_ASSESSED} are deliberately out: the change
     * is not yet at its decision point, and listing it as "waiting for the CAB" would make the queue
     * unreadable by filling it with work that is not the committee's yet. Everything at or past
     * {@code CAB_APPROVED} belongs to S8f's reconciliation, not here.
     */
    public static final List<String> AWAITING_CAB =
        List.of("CAB_PENDING", "CAB_DEFERRED", "MORE_INFO_REQUIRED");

    /** The gates that route to a committee. {@code AUTO} authorises without one, by definition. */
    public static final List<String> COMMITTEE_GATES = List.of("CAB", "ECAB");

    private final ChangeRequestRepository changeRepo;
    private final ChangeAuthorisationMatrixRepository matrixRepo;
    /** Absent on a deployment without the committee engine — reported, never assumed clean. */
    private final CommitteePort committeePort;

    public ChangeCabQueueService(ChangeRequestRepository changeRepo,
                                 ChangeAuthorisationMatrixRepository matrixRepo,
                                 @Autowired(required = false) CommitteePort committeePort) {
        this.changeRepo = changeRepo;
        this.matrixRepo = matrixRepo;
        this.committeePort = committeePort;
    }

    // ── the payload ──────────────────────────────────────────────────────────────────────────────

    /** One referral in a committee's open queue, with what this module knows about its subject. */
    public record Row(
        Long referralId,
        Long changeId,
        String changeCode,
        String subjectEn,
        String subjectAr,
        String referralStatus,
        String mode,
        Long meetingId,
        Long resolutionId,
        String subjectState,
        /** The change's own status, or null when the subject could not be found. */
        String changeStatus
    ) {
        /** Tabled and readable: the referral names a change that exists here. */
        public static final String PRESENT = "PRESENT";
        /** No V350 subject snapshot — real, open, and excluded from every agenda by design. */
        public static final String SUBJECT_NOT_STATED = "SUBJECT_NOT_STATED";
        /** The referral names a change that exists in no organisation. Two such rows in dev. */
        public static final String SUBJECT_NOT_FOUND = "SUBJECT_NOT_FOUND";
        public static final List<String> STATES = List.of(PRESENT, SUBJECT_NOT_STATED, SUBJECT_NOT_FOUND);

        public boolean isOrphan() { return !PRESENT.equals(subjectState); }
    }

    /** A change whose CAB decision is still ahead, with why it is not in front of the committee. */
    public record Pending(
        Long id,
        String changeCode,
        String titleEn,
        String titleAr,
        String changeType,
        String riskLevel,
        String status,
        String gate,
        Integer minApprovals,
        LocalDateTime scheduledStart,
        String reason
    ) {
        /**
         * No live committee is named for this gate, so nothing can be referred to it.
         *
         * <p>⚠ This rung was first written as {@code GATE_UNCONFIGURED} and keyed on the gate map's
         * {@code configured} flag — and the live probe caught it reporting every row that way on a gate
         * whose referral demonstrably <b>succeeded</b> (201) and whose agenda line appeared. The flag
         * folds three faults into one (F2-96's finding): {@code NOT_MAPPED}, {@code
         * COMMITTEE_UNAVAILABLE} and {@code ROSTER_EMPTY}. Only the first two stop a referral; the
         * third — dev's actual state, a named committee with no voting seats — stops the *decision*
         * landing while the queue keeps moving. Collapsing them told an operator to map a gate that was
         * already mapped correctly, which is precisely the remedy F2-96 was raised to prevent.
         *
         * <p>So the rung asks the narrow question it can answer — is a committee named? — and the
         * decision-blocking fault is a property of the {@link Gate}, true of every row at it or none.
         */
        public static final String GATE_UNMAPPED = "GATE_UNMAPPED";
        /** No live referral: the CAB has never been told this change is waiting for it. */
        public static final String NOT_REFERRED = "NOT_REFERRED";
        /**
         * Referred, but the change has no {@code change_code}, so no agenda line can name it.
         *
         * <p>F2-109 surfaced: nothing generates {@code change_code}, so every change created through
         * the API is unciteable. Kept apart from {@link #NOT_TABLED} because the remedy is different —
         * this one cannot be fixed by rebuilding the agenda.
         */
        public static final String SUBJECT_UNCITEABLE = "SUBJECT_UNCITEABLE";
        /** Referred to a committee with no future sitting: an item nobody will hear. */
        public static final String NO_SITTING_SCHEDULED = "NO_SITTING_SCHEDULED";
        /** The sitting's agenda carries no line naming this change — rebuild the agenda (S7b). */
        public static final String NOT_TABLED = "NOT_TABLED";
        /** Referred and named on the next sitting's agenda. Included so a reader sees the whole backlog. */
        public static final String TABLED = "TABLED";
        /** Ladder order: each rung is only reachable once the one above it is satisfied. */
        public static final List<String> REASONS = List.of(
            GATE_UNMAPPED, NOT_REFERRED, SUBJECT_UNCITEABLE, NO_SITTING_SCHEDULED, NOT_TABLED, TABLED);
    }

    /** One gate — which committee holds it, when it next sits, and what is in front of it. */
    public record Gate(
        String gate,
        Long committeeId,
        String committeeNameEn,
        String committeeNameAr,
        String committeeStatus,
        /** A live committee is named, so a change can be referred here. */
        boolean mapped,
        /**
         * The gate will actually carry a decision — A17-D2's own predicate, which additionally requires
         * at least one active voting seat.
         *
         * <p>Separate from {@link #mapped} because they differ in dev right now and the difference is
         * the whole of F2-96: committee 13 is correctly mapped and has zero voting members, so referral
         * and tabling work while the CAB approval is refused. A single flag would send the operator to
         * re-map a mapping that is already right.
         */
        boolean canDecide,
        /** A17-D2's stated reason the gate cannot decide, or null. Rendered verbatim, never invented. */
        String fault,
        CommitteePort.Sitting sitting,
        List<Row> queue,
        List<Pending> awaiting
    ) { }

    /** The workspace's whole answer. */
    public record CabQueue(
        List<Gate> gates,
        long awaiting,
        long notReferred,
        long notTabled,
        long orphans,
        long tabled,
        Map<String, Long> byReason,
        /** True when the committee engine is not on this deployment: unknown, not clean (S8h). */
        boolean engineAbsent
    ) { }

    // ── the composition ─────────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CabQueue queue() {
        Long org = TenantContext.getCurrentTenant();

        // Every change whose committee decision is still ahead, with the matrix cell that says which
        // gate decides it. A missing cell is not treated as AUTO: it means the tenant has no policy for
        // this (type × risk), which S8f already reports as a finding, and inventing a gate here would
        // put a change in a queue no rule sent it to.
        Map<String, ChangeAuthorisationMatrix> cells = new HashMap<>();
        for (ChangeAuthorisationMatrix m : matrixRepo.findMatrixRanked(org))
            cells.put(cellKey(m.getChangeType(), m.getRiskLevel()), m);

        Map<String, List<ChangeRequest>> byGate = new LinkedHashMap<>();
        for (String g : COMMITTEE_GATES) byGate.put(g, new ArrayList<>());
        for (ChangeRequest c : changeRepo.findByStatuses(org, AWAITING_CAB)) {
            ChangeAuthorisationMatrix cell = cells.get(cellKey(c.getChangeType(), c.getRiskLevel()));
            String gate = cell == null ? null : cell.getGate();
            if (gate != null && byGate.containsKey(gate)) byGate.get(gate).add(c);
        }

        boolean engineAbsent = committeePort == null;
        List<Map<String, Object>> gateMap = engineAbsent ? List.of() : committeePort.gateMap();

        List<Gate> gates = new ArrayList<>();
        for (String gate : COMMITTEE_GATES) {
            Map<String, Object> cfg = gateMap.stream()
                .filter(row -> gate.equals(row.get("gate"))).findFirst().orElse(Map.of());
            Long committeeId = asLong(cfg.get("committeeId"));
            // A17-D2's predicate is stricter than "a committee is named": it also demands an active
            // voting seat. Both facts are carried, because they differ and the difference is F2-96.
            boolean canDecide = Boolean.TRUE.equals(cfg.get("configured")) && !engineAbsent;
            String fault = engineAbsent
                ? "The committee engine is not available on this deployment, so this gate cannot be "
                  + "resolved. Nothing here should be read as a clean queue."
                : str(cfg.get("fault"));

            // An unmapped gate has no committee to ask, and asking anyway is how a null committee id
            // reaches four layers down before anyone notices. Found by this slice's own unit test:
            // the ladder's first rung was written before the read it guards.
            boolean mapped = !engineAbsent && committeeId != null
                && !"DISSOLVED".equals(str(cfg.get("committeeStatus")));
            CommitteePort.CommitteeQueue open = mapped
                ? committeePort.queue(committeeId) : CommitteePort.CommitteeQueue.empty();
            CommitteePort.Sitting sitting = mapped
                ? committeePort.nextSitting(committeeId).orElse(null) : null;

            List<Row> rows = rowsFor(org, open);
            List<Pending> awaiting = awaitingFor(byGate.get(gate), rows, cells, gate, mapped, sitting);

            gates.add(new Gate(gate, committeeId, str(cfg.get("committeeNameEn")),
                str(cfg.get("committeeNameAr")), str(cfg.get("committeeStatus")),
                mapped, canDecide, fault, sitting, rows, awaiting));
        }

        Map<String, Long> byReason = new LinkedHashMap<>();
        for (String r : Pending.REASONS) byReason.put(r, 0L);
        long orphans = 0;
        for (Gate g : gates) {
            for (Pending p : g.awaiting()) byReason.merge(p.reason(), 1L, Long::sum);
            orphans += g.queue().stream().filter(Row::isOrphan).count();
        }

        return new CabQueue(gates,
            gates.stream().mapToLong(g -> g.awaiting().size()).sum(),
            byReason.getOrDefault(Pending.NOT_REFERRED, 0L),
            byReason.getOrDefault(Pending.NOT_TABLED, 0L),
            orphans,
            byReason.getOrDefault(Pending.TABLED, 0L),
            byReason, engineAbsent);
    }

    /**
     * Turn a committee's open referrals into rows, resolving each subject against this module's own
     * table — the check the committee engine is structurally unable to perform.
     */
    private List<Row> rowsFor(Long org, CommitteePort.CommitteeQueue open) {
        List<Row> out = new ArrayList<>();
        for (CommitteePort.ReferralView r : open.stated()) out.add(row(org, r, false));
        for (CommitteePort.ReferralView r : open.unstated()) out.add(row(org, r, true));
        return out;
    }

    private Row row(Long org, CommitteePort.ReferralView r, boolean unstated) {
        Optional<ChangeRequest> subject = "CHANGE".equals(r.sourceModule()) && r.sourceEntityId() != null
            ? changeRepo.findById(r.sourceEntityId()).filter(c -> Objects.equals(org, c.getOrganisationId()))
            : Optional.empty();
        // Order matters: a referral can be BOTH unstated and orphaned, and "the change does not exist"
        // is the more actionable of the two — a snapshot cannot be backfilled for a row that has no
        // subject to snapshot. Reporting the softer state first would send someone to write a label.
        String state = subject.isEmpty() ? Row.SUBJECT_NOT_FOUND
                     : unstated ? Row.SUBJECT_NOT_STATED
                     : Row.PRESENT;
        return new Row(r.id(), r.sourceEntityId(), r.sourceCode(), r.subjectEn(), r.subjectAr(),
            r.status(), r.mode(), r.meetingId(), r.resolutionId(), state,
            subject.map(ChangeRequest::getStatus).orElse(null));
    }

    /** Why each change at this gate is, or is not, in front of the committee. */
    private List<Pending> awaitingFor(List<ChangeRequest> changes, List<Row> rows,
                                      Map<String, ChangeAuthorisationMatrix> cells, String gate,
                                      boolean mapped, CommitteePort.Sitting sitting) {
        Map<Long, Row> referredBy = new HashMap<>();
        for (Row r : rows) if (r.changeId() != null) referredBy.put(r.changeId(), r);

        List<Pending> out = new ArrayList<>();
        for (ChangeRequest c : changes) {
            ChangeAuthorisationMatrix cell = cells.get(cellKey(c.getChangeType(), c.getRiskLevel()));
            Row referral = referredBy.get(c.getId());
            // A ladder, deliberately ordered by what a reader must fix first: an unmapped gate makes
            // referral impossible, an unreferred change makes tabling impossible, an unciteable change
            // makes an agenda line impossible, and only then is "the agenda has not been rebuilt" the
            // answer. Collapsing any two of these produces a count nobody can act on.
            //
            // ⚠ The first rung is `mapped`, NOT the gate map's `configured`. See GATE_UNMAPPED's javadoc:
            // keying it on `configured` reported every row unfixable on a gate whose referral succeeded.
            String reason;
            if (!mapped)                                       reason = Pending.GATE_UNMAPPED;
            else if (referral == null)                         reason = Pending.NOT_REFERRED;
            else if (isBlank(c.getChangeCode()))               reason = Pending.SUBJECT_UNCITEABLE;
            else if (sitting == null)                          reason = Pending.NO_SITTING_SCHEDULED;
            else if (!sitting.tables(c.getChangeCode()))       reason = Pending.NOT_TABLED;
            else                                               reason = Pending.TABLED;
            out.add(new Pending(c.getId(), c.getChangeCode(), c.getTitleEn(), c.getTitleAr(),
                c.getChangeType(), c.getRiskLevel(), c.getStatus(), gate,
                cell == null ? null : cell.getMinApprovals(), c.getScheduledStart(), reason));
        }
        return out;
    }

    private static String cellKey(String type, String risk) {
        return (type == null ? "" : type) + "|" + (risk == null ? "" : risk);
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.valueOf(String.valueOf(o)); } catch (NumberFormatException e) { return null; }
    }
}
