package com.tss.grc.module.admin.committee.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Appointing a member to a committee's roster (finding F2-25).
 *
 * <p><b>Why this DTO exists.</b> The endpoint bound the {@code CommitteeMemberTerm} JPA
 * entity directly with no {@code @Valid} and no bean-validation anywhere on the path, so
 * the entity's {@code nullable = false} columns were the <em>only</em> statement of what a
 * caller had to supply — and they were enforced at the database, not at the boundary. Two
 * of them ({@code appointed_at}, {@code term_starts_at}) have no default, so an otherwise
 * reasonable body died as a {@code DataIntegrityViolation} and surfaced as
 * <b>500 "An unexpected database error occurred."</b> An authorised secretary could not
 * appoint a member, and the API could not say why.
 *
 * <p><b>What this DTO deliberately does NOT change.</b> The service was already safe:
 * {@code CommitteeMemberRegistryService.appoint()} nulls {@code id} and stamps
 * {@code organisationId} from {@code TenantContext}, and has since {@code 8373d6f8}. F2-25
 * originally claimed a cross-tenant mass-assignment vector here; a review-council live probe
 * disproved it (`{"id": 424242, "organisationId": 2}` → 200, saved as
 * {@code id: 78, organisationId: 1}) and the finding was corrected from P1 to P2. Removing
 * the fields from the contract is still right — it stops the API *implying* they are
 * settable — but it is a contract fix, not a security fix.
 *
 * <p><b>Why the two dates are mandatory rather than defaulted to today.</b> Defaulting them
 * would be easier on the caller and is the wrong call: {@code appointedAt} and
 * {@code termStartsAt} are governance facts that feed CMA term-limit counting (Article 27
 * caps consecutive terms) and the quorum denominator F2-28 is about to make load-bearing.
 * Silently stamping "today" onto an appointment that happened at a board meeting last month
 * fabricates provenance — the same thing V315 and V316 refuse to do for votes and approvals.
 * Better to make the caller state the date.
 *
 * <p>Ordering landmine: {@code @Valid} runs <b>before</b> {@code @PreAuthorize}, so a probe
 * of the authorisation behaviour still needs a body that satisfies these constraints.
 */
public record MemberTermRequest(

        /** The committee being appointed to. Must resolve within the caller's tenant. */
        @NotNull Long committeeId,

        /** Display name as it appears on the charter and in the minutes. */
        @NotBlank @Size(max = 255) String memberName,

        /** Optional link to a platform user. Left null for external directors who have no
         *  login — see F2-26, which makes this the basis for a member casting their own
         *  vote rather than every vote being secretariat-recorded on their behalf. */
        Long memberUserId,

        /** Chair | Vice-Chair | Member | Secretary | Standing Invitee | External Advisor. */
        @NotBlank @Size(max = 120) String role,

        /** 1 = counts toward quorum and may cast a counted vote; 0 = attends only.
         *  Defaults to 1 when omitted, matching the entity. */
        Short isVotingMember,

        /** 1 = independent under the CMA/SAMA independence test. Defaults to 0. */
        Short isIndependent,

        /** Free-text justification the CMA Corporate Governance Regulation requires
         *  committees to document for an independence claim. */
        @Size(max = 500) String independenceBasisEn,

        /** When the appointment was made. **Mandatory — never defaulted** (see above). */
        @NotNull LocalDate appointedAt,

        /** Board-resolution code that appointed this member, for traceability back to the
         *  resolution register. */
        @Size(max = 80) String appointedByReso,

        /** When the term begins. **Mandatory — never defaulted** (see above). */
        @NotNull LocalDate termStartsAt,

        /** NULL for indefinite terms (management / ex-officio members). */
        LocalDate termEndsAt,

        /** Consecutive term number; defaults to 1. CMA Article 27 caps at 3 for
         *  non-executive directors of public companies. */
        Integer termNumber
) {}
