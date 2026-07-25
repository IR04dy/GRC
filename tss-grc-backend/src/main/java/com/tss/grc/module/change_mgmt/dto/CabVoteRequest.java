package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A CAB vote on a change (DF-4 / finding F2-24).
 *
 * <p><b>Note what is absent: there is no approver name and no approver id.</b> The
 * approver is the authenticated principal, always. This endpoint previously accepted the
 * {@code ChangeApproval} JPA entity directly, which let a caller name any approver — and
 * because the CM-C2 segregation-of-duties gate compared that <em>claimed</em> name against
 * the change's requester and owner, a requester could approve their own change by
 * submitting somebody else's name. Removing the field is the fix; validating it would not
 * have been.
 *
 * <p>Binding a DTO rather than the entity also closes this endpoint's instance of the
 * mass-assignment class (F2-22): {@code id} and every other persisted column are no longer
 * client-settable.
 *
 * <p><b>No on-behalf path here, deliberately.</b> Unlike committee votes — where a
 * secretary legitimately minutes a show of hands and the row is stamped
 * {@code on_behalf = 1} — this is the <em>direct</em> CAB path, and a change routed to a
 * committee is blocked from it (Track D) precisely so the two cannot both satisfy
 * {@code CAB_APPROVED}. If a vote needs recording on someone's behalf, the change belongs
 * on the committee route, which supports it with provenance.
 *
 * <p>Ordering landmine: {@code @Valid} runs <b>before</b> {@code @PreAuthorize}, so a probe
 * of the authorisation behaviour still needs a body that satisfies these constraints.
 */
public record CabVoteRequest(

        /** APPROVE | REJECT | DEFER | ABSTAIN — validated against the permitted set. */
        @NotBlank String decision,

        /** The capacity in which the approver is voting (e.g. "CISO", "Change Manager").
         *  A label for the minutes — never an identity claim. */
        @Size(max = 100) String approverRole,

        /** Free-text rationale, recorded on the vote and visible in the CAB record. */
        String comments
) {}
