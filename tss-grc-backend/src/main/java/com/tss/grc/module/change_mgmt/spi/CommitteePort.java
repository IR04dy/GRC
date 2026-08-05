package com.tss.grc.module.change_mgmt.spi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * S5 / CE-1 — the change module's outbound port to the committee engine (ADR-012).
 *
 * <p><b>The rule this exists to make true.</b> `module/change**` may import only itself,
 * `common.*`, `security.*`, `platform.*` and its own `spi` interfaces — never another
 * `module.<peer>`. Before this port the change module imported
 * `admin.committee.entity.CommitteeReferral`, `admin.committee.service.CommitteeReferralService`
 * and `admin.committee.service.CommitteeGateMapService` directly, so "Change Enablement is a
 * separate module" was a statement about directory layout rather than about dependencies.
 * `ChangeModuleBoundaryTest` now fails if any of them come back.
 *
 * <p><b>Why the port carries records and not the peer's entity.</b> A port that returns
 * `CommitteeReferral` moves the import one file sideways and changes nothing: the change module
 * would still be compiled against a JPA entity owned by `admin`, still break when a column is
 * added there, and still be unable to compile without it. {@link ReferralView} is a change-owned
 * projection, deliberately field-for-field with the same JSON names, so the HTTP payload the
 * frontend already consumes is byte-identical — verified live, not assumed.
 *
 * <p><b>What is deliberately NOT here.</b> Quorum, attendance, minutes, e-signatures and vote
 * tallies. A CAB is a *profile over* the committee engine, not a parallel implementation (§6c) —
 * the moment this port grows a `recordVote` the change module has started reimplementing the
 * engine, and the platform has two answers to "was this decision quorate?" (F-014's defect class,
 * and F2-28's forgeable-quorum surface doubled).
 *
 * <p>The implementation lives in {@code module.change_adapters}, <b>outside</b> both modules, so
 * neither depends on the other. It is injected as {@code @Autowired(required = false)}: when
 * absent the referral paths no-op, exactly as the direct service did, which keeps every
 * directly-constructed unit test working.
 */
public interface CommitteePort {

    /**
     * A committee referral, as the change module needs to see it.
     *
     * <p>Field names and order mirror {@code admin.committee.entity.CommitteeReferral} because two
     * change endpoints return this shape over HTTP and the frontend reads it. Changing the payload
     * to tidy the port would have been a silent API break dressed as a refactor.
     */
    record ReferralView(
        Long id,
        Long organisationId,
        String sourceModule,
        Long sourceEntityId,
        Long committeeId,
        Long meetingId,
        Long resolutionId,
        String gate,
        String mode,
        String status,
        String referredBy,
        String recusalNames,
        LocalDateTime createdAt,
        // S7b / V350 — appended, not interleaved: the existing keys keep their positions so the
        // payload the frontend already reads is unchanged, and these are additive.
        String sourceCode,
        String subjectEn,
        String subjectAr
    ) { }

    /**
     * S7b / CE-4 — what the committee is being asked to decide, as this module states it.
     *
     * <p>A change-owned record mirroring {@code CommitteeReferralService.SubjectRef}, for the same
     * reason {@link ReferralView} mirrors the entity: a port that took the peer's type would move
     * the import one file sideways and leave this module compiled against {@code admin}.
     *
     * <p>It exists because the agenda is built in the committee module and cannot read
     * {@code change_requests} to find out what it is deciding — S5a took those imports to zero and
     * {@code ChangeModuleBoundaryTest} keeps them there. So the referring module states the label
     * once, at referral time, and the referral keeps it (V350).
     */
    record SubjectRef(String code, String titleEn, String titleAr) { }

    /**
     * CE-13 — a committee's open queue, split by whether the item can be named on an agenda.
     *
     * <p>Mirrors {@code CommitteeReferralService.Queue}. The partition is the engine's, computed by the
     * engine's own predicate: this module must not re-derive "does this referral carry a subject
     * snapshot?", because a second copy of a rule is a mirror maintained by assertion and it lies the
     * first time one side changes (S8h's keeper, one subsystem across).
     *
     * @param stated   agenda-ready — the committee has been told what it is deciding
     * @param unstated real, open, unnameable referrals: excluded from every agenda for a stated reason
     *                 and, until CE-13, reported to nobody
     */
    record CommitteeQueue(List<ReferralView> stated, List<ReferralView> unstated) {
        public static CommitteeQueue empty() { return new CommitteeQueue(List.of(), List.of()); }
        public int total() { return stated.size() + unstated.size(); }
    }

    /**
     * CE-13 — the committee's next sitting, as the CAB workspace needs to name it.
     *
     * <p><b>Why this does not breach the exclusion list above.</b> This port must not carry quorum,
     * attendance, minutes, e-signatures or tallies, because those are the engine's answers and a second
     * answer is the defect. A sitting's <em>identity</em> — which meeting, when, in what lifecycle
     * state, how many items are on its agenda and how many of them were tabled by this module — is
     * none of those. Nothing here can be used to decide whether a decision was quorate; it exists so a
     * reader can be sent to the meeting rather than told a decision is "pending" with no idea where.
     *
     * <p>{@code tabledSubjects} is the agenda text of every line this module put there (S7b). Empty
     * while {@link CommitteeQueue#stated} is non-empty is exactly the gap the workspace exists to show:
     * the queue exists and the agenda has not been rebuilt to carry it. Each line names the record by
     * the reference it was tabled under, which is how a caller tells "three items are tabled" from
     * "<em>this</em> item is tabled".
     */
    record Sitting(
        Long id,
        String code,
        String titleEn,
        String titleAr,
        LocalDateTime scheduledAt,
        String status,
        int agendaItems,
        List<String> tabledSubjects
    ) {
        public int itemsForDecision() { return tabledSubjects == null ? 0 : tabledSubjects.size(); }

        /**
         * True when an agenda line tables this reference.
         *
         * <p>An exact match on the line's own reference field, with {@code contains} as the fallback for
         * lines written before that field existed — those carry only their title, which embeds the
         * reference. Tolerant on read; the builder now writes the field.
         *
         * <p>Blank references never match — and that is a distinct finding, not a false negative: a
         * change with no {@code change_code} cannot be named on any agenda at all (F2-109). The caller
         * must report that separately rather than let it read as "not tabled yet".
         */
        public boolean tables(String reference) {
            if (reference == null || reference.isBlank() || tabledSubjects == null) return false;
            return tabledSubjects.stream()
                .anyMatch(s -> s != null && (s.equals(reference) || s.contains(reference)));
        }
    }

    /** One row of the tenant's gate → committee map (A17-D2's config surface). */
    record GateMapping(
        Long id,
        Long organisationId,
        String gate,
        Long committeeId,
        String committeeNameEn,
        String committeeNameAr,
        String committeeStatus
    ) { }

    // ── referrals ────────────────────────────────────────────────────────────

    /** The live referral for a source record, if it has one. */
    Optional<ReferralView> activeReferral(String sourceModule, Long sourceEntityId);

    /** Refer a record to a committee under a gate. Idempotent on (tenant, module, record). */
    ReferralView refer(String sourceModule, Long sourceEntityId, String gate, String mode,
                       Long committeeId, String referredBy, String recusalNames,
                       SubjectRef subject);

    /** Bind a carried committee resolution to the referral — the decision write-back. */
    ReferralView linkResolution(String sourceModule, Long sourceEntityId, Long resolutionId);

    /**
     * Throw unless the referral's carried resolution approves with at least {@code minApprovals}.
     *
     * <p>The assertion stays on the engine's side of the port on purpose: it reads the resolution
     * tally, the meeting's quorum source and the minutes' approval state, and re-deriving any of
     * that here is how a second, weaker answer to "was this quorate?" gets written.
     */
    void assertDecisionApproves(String sourceModule, Long sourceEntityId, int minApprovals);

    /**
     * S8f / CE-11 — the same question as {@link #assertDecisionApproves}, asked without throwing.
     *
     * <p><b>Why this had to exist.</b> {@code CAB_APPROVED} has <b>two</b> legitimate routes: recorded
     * {@code change_approvals} votes, or — for a referred change — the committee's carried resolution,
     * which is the system of record and writes <em>no</em> vote rows in this module (Track D). The
     * unauthorised-change reconciliation reads the vote table, so without this method it would report
     * every committee-routed change as having no authorisation on record — reporting the platform's
     * <em>preferred</em> route, the one S6 and S7 exist to provide, as an unauthorised change.
     *
     * <p>It is a predicate and not a re-derivation: the implementation delegates to the same engine
     * call {@link #assertDecisionApproves} uses, so there is still exactly one answer to "was this
     * quorate?" — the thing this port's javadoc warns about two paragraphs up. What it must never
     * become is a local tally.
     *
     * <p>Returns {@code false} when there is no referral, no carried resolution, or the resolution
     * does not approve — a reconciliation that cannot see authority must not assume it (S6b-i).
     */
    boolean decisionApproves(String sourceModule, Long sourceEntityId, int minApprovals);

    /** Add resolution outcome/tally and meeting status/quorum to a response map. */
    void describe(ReferralView referral, Map<String, Object> out);

    // ── CE-13: the queue, and where it will be heard ─────────────────────────

    /** Everything this committee has open, partitioned. Empty (never null) when the engine is absent. */
    CommitteeQueue queue(Long committeeId);

    /**
     * The committee's next sitting, or empty when it has none scheduled ahead of now.
     *
     * <p>Empty is a real answer and must be rendered as one: a queue with no sitting is an item nobody
     * will hear, not a missing field.
     */
    Optional<Sitting> nextSitting(Long committeeId);

    // ── the tenant's gate → committee map (A17-D2) ───────────────────────────

    List<Map<String, Object>> gateMap();

    List<Map<String, Object>> eligibleCommittees();

    Map<String, Object> mapGate(String gate, Long committeeId);

    void unmapGate(String gate);
}
