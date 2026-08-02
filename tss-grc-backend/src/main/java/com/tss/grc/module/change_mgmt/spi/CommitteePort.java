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
        LocalDateTime createdAt
    ) { }

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
                       Long committeeId, String referredBy, String recusalNames);

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

    /** Add resolution outcome/tally and meeting status/quorum to a response map. */
    void describe(ReferralView referral, Map<String, Object> out);

    // ── the tenant's gate → committee map (A17-D2) ───────────────────────────

    List<Map<String, Object>> gateMap();

    List<Map<String, Object>> eligibleCommittees();

    Map<String, Object> mapGate(String gate, Long committeeId);

    void unmapGate(String gate);
}
