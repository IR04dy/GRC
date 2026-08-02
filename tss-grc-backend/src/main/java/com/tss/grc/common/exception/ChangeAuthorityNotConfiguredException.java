package com.tss.grc.common.exception;

/**
 * ADR-017 <b>D-2</b> — a CAB-dependent approval was attempted while the tenant has not named the
 * committee that its own change-authorisation matrix demands. The transition <b>fails closed</b>.
 *
 * <p><b>Why this is a distinct exception rather than an {@link IllegalStateException}.</b> D-2's rule
 * is "not a 500, and not a silent pass" — but it also says the refusal must be <em>actionable</em>,
 * naming the missing configuration and the screen that sets it. A bare 409 carrying only a sentence
 * cannot be acted on by the frontend: it has no way to route the administrator to the fix. So the
 * fields below are exposed as structured ProblemDetail properties, for the reason
 * {@code ModuleDisabledException} exposes {@code moduleCode} — F2-80's shape is a handler that
 * discards what the thrower knew.
 *
 * <p><b>{@link #getReason()} distinguishes two different fixes,</b> which is the whole point of
 * carrying it:
 * <ul>
 *   <li>{@code NOT_MAPPED} — no {@code admin.committee_gate_map} row for this (tenant, gate). The
 *       administrator constitutes a committee and maps it.</li>
 *   <li>{@code COMMITTEE_UNAVAILABLE} — a row exists but points at a committee that is missing,
 *       soft-deleted or {@code DISSOLVED}. The administrator re-points the gate. This case used to be
 *       invisible: {@code ChangeGovernanceHealthService} reported the gate <em>healthy</em> because a
 *       row existed, while {@code CommitteeReferralService.refer} rejected the dissolved committee —
 *       the report and the enforcement disagreed, which is the F2-62 shape. They now share one
 *       predicate, and it includes the committee's own state.</li>
 * </ul>
 *
 * @see com.tss.grc.module.change_mgmt.service.ChangeGovernanceHealthService
 */
public class ChangeAuthorityNotConfiguredException extends RuntimeException {

    /** No gate-map row exists for this (tenant, gate). */
    public static final String NOT_MAPPED = "NOT_MAPPED";
    /** A row exists but its committee is absent, soft-deleted or DISSOLVED. */
    public static final String COMMITTEE_UNAVAILABLE = "COMMITTEE_UNAVAILABLE";

    /**
     * F2-96 — a live row points at a live committee that <b>cannot ever be quorate</b>, because it
     * has no ACTIVE voting seat. Distinguished from {@link #COMMITTEE_UNAVAILABLE} because the
     * remedy is different: the committee exists and is correctly mapped, so the administrator must
     * appoint members rather than re-map a gate. Measured 2026-08-02 in dev AND prod — committee 13,
     * the CAB gate's own target, had zero active voting seats in both while
     * {@code governance-health} reported {@code healthy: true}.
     */
    public static final String ROSTER_EMPTY = "ROSTER_EMPTY";

    /** The screen that sets the configuration. Named in the refusal, per D-2. */
    public static final String REMEDY_SCREEN = "/committees";
    /** The API the screen calls, for integrators driving onboarding headlessly. */
    public static final String REMEDY_API = "PUT /api/v1/changes/authority/{gate}";
    /** The configuration the refusal is about. */
    public static final String CONFIGURATION = "admin.committee_gate_map";

    private final String gate;
    private final Long organisationId;
    private final String reason;
    private final String target;

    public ChangeAuthorityNotConfiguredException(String message, String gate, Long organisationId,
                                                String reason, String target) {
        super(message);
        this.gate = gate;
        this.organisationId = organisationId;
        this.reason = reason;
        this.target = target;
    }

    /** {@code CAB} or {@code ECAB} — the authority the matrix demands for this change. */
    public String getGate() { return gate; }

    public Long getOrganisationId() { return organisationId; }

    /** {@link #NOT_MAPPED}, {@link #COMMITTEE_UNAVAILABLE} or {@link #ROSTER_EMPTY}. */
    public String getReason() { return reason; }

    /** The transition that was refused (e.g. {@code CAB_APPROVED}). */
    public String getTarget() { return target; }
}
