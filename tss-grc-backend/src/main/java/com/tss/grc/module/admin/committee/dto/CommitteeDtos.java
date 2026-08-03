package com.tss.grc.module.admin.committee.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.tss.grc.common.model.CommitteeTypes;
import com.tss.grc.module.admin.committee.entity.Committee;
import com.tss.grc.module.admin.committee.entity.CommitteeMeeting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class CommitteeDtos {
    private CommitteeDtos() {}

    // ── Committee ────────────────────────────────────────────────────
    public record CommitteeCreateRequest(
            @NotBlank @Size(max = 64) String code,
            @NotBlank @Size(max = 255) String nameEn,
            // F2-30 — the DTO carried the same asymmetry as the entity: nameEn mandatory,
            // nameAr free. This is Committee's real write path (the entity constraint is the
            // backstop for service-layer saves), and it yields a field-naming 400 via the
            // long-standing MethodArgumentNotValidException handler.
            @NotBlank @Size(max = 255) String nameAr,
            String descriptionEn, String descriptionAr,
            // W1-5 — found by the golden-path integration test on its very first call.
            // `@Size(max = 30)` was the whole contract, so `CK_com_type` was the only enforcement
            // of the vocabulary and an off-vocabulary value arrived as a ConstraintViolation:
            // 500 "An unexpected database error occurred." on a committee-creation request that
            // was otherwise well formed. Same shape as F2-25 (the two mandatory dates on the
            // sibling appoint endpoint) and F2-88 (a duplicate code) — an entity-backed write
            // whose only statement of what is legal lives in the database.
            //
            // ⚠ `CAB` is deliberately NOT in this vocabulary. ADR-017 D-2: the CAB is a *role* a
            // tenant assigns to one of its committees through `admin.committee_gate_map`, not a
            // committee type. Do not "fix" a rejected `committeeType: "CAB"` by widening this.
            @NotBlank @Size(max = 30)
            @Pattern(regexp = CommitteeTypes.REGEX, message = CommitteeTypes.MESSAGE)
            String committeeType,
            String chairpersonName, String chairpersonRole,
            String secretaryName,
            String membersJson, String charterEn, String charterAr,
            String meetingCadence, Integer quorumPct,
            /**
             * S6c — the charter requires someone presiding for the meeting to be quorate.
             * Nullable: absent means "not stated", which the service reads as false. A flag with
             * no way to set it through the API is a dead control (A17-D2's whole subject), so it
             * is on create AND update, and echoed on the response.
             */
            Boolean quorumRequiresChair
    ) {}

    public record CommitteeUpdateRequest(
            String nameEn, String nameAr,
            String descriptionEn, String descriptionAr,
            String chairpersonName, String chairpersonRole, String secretaryName,
            String membersJson, String charterEn, String charterAr,
            String meetingCadence, Integer quorumPct,
            /** S6c — null leaves the existing setting alone; the update is a partial patch. */
            Boolean quorumRequiresChair
    ) {}

    public record CommitteeDissolveRequest(@NotBlank @Size(max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CommitteeResponse(
            Long id, String code, String externalId,
            String nameEn, String nameAr,
            String descriptionEn, String descriptionAr,
            String committeeType,
            String chairpersonName, String chairpersonRole, String secretaryName,
            String membersJson, String charterEn, String charterAr,
            String meetingCadence, Integer quorumPct,
            /** S6c — always present, so a caller can see the clause is OFF rather than absent. */
            Boolean quorumRequiresChair,
            String status,
            LocalDateTime dissolvedAt, String dissolvedReason,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static CommitteeResponse from(Committee c) {
            return new CommitteeResponse(
                    c.getId(), c.getCode(), c.getExternalId(),
                    c.getNameEn(), c.getNameAr(),
                    c.getDescriptionEn(), c.getDescriptionAr(),
                    c.getCommitteeType(),
                    c.getChairpersonName(), c.getChairpersonRole(), c.getSecretaryName(),
                    c.getMembersJson(), c.getCharterEn(), c.getCharterAr(),
                    c.getMeetingCadence(), c.getQuorumPct(),
                    // S6c: never null on the wire. The entity defaults FALSE and V348 makes the
                    // column NOT NULL, but a legacy row read before the migration would be null,
                    // and `@JsonInclude(NON_NULL)` would then drop the field entirely — a caller
                    // could not tell "clause off" from "field does not exist".
                    Boolean.TRUE.equals(c.getQuorumRequiresChair()),
                    c.getStatus(),
                    c.getDissolvedAt(), c.getDissolvedReason(),
                    c.getCreatedAt(), c.getUpdatedAt());
        }
    }

    // ── Meeting ──────────────────────────────────────────────────────
    public record MeetingCreateRequest(
            @NotNull Long committeeId,
            String titleEn, String titleAr,
            @NotNull LocalDateTime scheduledAt,
            String locationOrLink,
            String agendaJson,
            String chairName, String secretaryName
    ) {}

    public record MeetingUpdateRequest(
            String titleEn, String titleAr,
            LocalDateTime scheduledAt,
            String locationOrLink,
            String agendaJson,
            String chairName, String secretaryName
    ) {}

    public record MeetingMinutesRequest(
            String minutesEn, String minutesAr,
            String attendeesJson, Short quorumMet,
            String decisionsJson, String actionItemsJson
    ) {}

    public record MeetingCancelRequest(@NotBlank @Size(max = 500) String reason) {}

    // ── CH-14 structured attendance · F2-28 seat-bound quorum ────────────────

    /**
     * <b>F2-28 / DF-5 — input.</b> One mark per person: either a roster seat's presence, or a
     * declared guest.
     *
     * <p><b>Why this replaced the old input shape.</b> {@code AttendeeRow} carried a free-text
     * {@code memberName} <em>and</em> a client-set {@code votingMember}, and
     * {@code recordAttendance} rebuilt the whole sheet from it — so the caller chose both the
     * numerator and the denominator of quorum. Invent six voting attendees, mark four present,
     * and a 67% committee is quorate. Here the caller supplies only <b>who was in the room</b>;
     * identity comes from the roster seat and eligibility comes with it.
     *
     * <p>Absence needs no mark. A voting seat with no mark is <b>absent</b>, and still counts in
     * the denominator — which is the whole point of quorum. Marking absences explicitly is
     * therefore optional courtesy, not a load-bearing act, and forgetting one can only ever
     * make a meeting look <em>less</em> quorate.
     */
    public record AttendanceMark(
            /** The roster seat. NULL means this is a guest, and {@code guestName} is required. */
            Long memberTermId,
            /** Guest's name, for the minutes. Required iff {@code memberTermId} is NULL. */
            @Size(max = 255) String guestName,
            /** Guest's role/affiliation ("External auditor — KPMG"). */
            @Size(max = 120) String guestRole,
            Boolean present,
            Boolean coiDeclared,
            @Size(max = 1000) String coiNote
    ) {}

    /**
     * <b>Replaces the whole sheet</b>, so the collection must be <em>present</em> — {@code @NotNull},
     * deliberately not {@code @NotEmpty}.
     *
     * <p><b>Found by S6c's own live probe, which posted the wrong key.</b> The platform sets
     * {@code spring.jackson.deserialization.fail-on-unknown-properties: false} (a deliberate
     * forward-compatibility choice), so {@code {"attendees": [...]}} deserialised to
     * {@code marks == null}, the service read that as {@code List.of()}, and the endpoint answered
     * <b>200</b> having <em>deleted the attendance sheet</em> and recomputed quorum to not-met. It
     * fails closed and the ledger records "4 row(s) → 0", so nothing was hidden — but a typo in a
     * key name should not be able to erase governance evidence.
     *
     * <p>{@code []} stays legal and means "nobody attended", which is a real thing to record: per
     * {@link AttendanceMark}, an unmarked voting seat is <em>absent</em> and still counts in the
     * denominator. So the distinction that matters is <b>absent field</b> (probably a mistake, now a
     * 400 naming {@code marks}) versus <b>empty list</b> (an assertion the caller meant to make).
     */
    public record AttendanceRequest(@NotNull java.util.List<AttendanceMark> marks) {}

    /** <b>Output only.</b> {@code votingMember} is resolved from the seat, never echoed from a
     *  request — the field's presence here is what made the old shape ambiguous. */
    public record AttendeeRow(
            Long memberTermId,
            @NotBlank @Size(max = 255) String memberName,
            @Size(max = 120) String memberRole,
            Boolean votingMember, Boolean present,
            Boolean coiDeclared, String coiNote,
            /** TRUE = a non-member who attended; never counts toward quorum. */
            Boolean guest
    ) {}

    /**
     * <b>F2-28 / DF-5.</b> The quorum arithmetic, with its provenance.
     *
     * @param totalVoting      the <b>denominator</b>: active voting seats on the committee's
     *                         roster — <em>not</em> a count of attendance rows. An absent member
     *                         counts here, which is what quorum means
     * @param presentVoting    voting seats marked present
     * @param requiredVoting   {@code ceil(totalVoting × quorumPct / 100)}, minimum 1
     * @param quorumSource     {@code COMPUTED} · {@code ASSERTED_LEGACY} (a flag a caller
     *                         supplied before V319 — 17 meetings, 15 e-signed) · {@code null}
     * @param computedAt       when the arithmetic was persisted; a past quorum does not move
     *                         when the roster later changes
     * @param unrecordedVoting voting seats with no mark at all — absent by default. Surfaced so
     *                         the "absence needs no mark" rule is visible rather than silent
     * @param rosterUnlinked   voting seats not linked to a platform user (F2-26). Not a quorum
     *                         input; reported here because it is the reason a member may be
     *                         unable to cast their own vote at this meeting
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuorumView(
            Long meetingId, Integer quorumPct,
            int totalVoting, int presentVoting, int requiredVoting,
            boolean quorumMet,
            String quorumSource,
            java.time.LocalDateTime computedAt,
            int unrecordedVoting,
            int rosterUnlinked,
            /**
             * S6c — the committee's charter requires someone presiding for the meeting to be
             * quorate. Reported even when false, so a caller can tell "the clause does not apply"
             * from "the clause applies and is satisfied": a report that only mentions a rule when it
             * bites cannot be used to check the rule is configured.
             */
            boolean chairRequired,
            /** S6c — a present, non-guest attendee whose seat presides (chair or deputy). */
            boolean chairPresent,
            java.util.List<AttendeeRow> attendees
    ) {}

    /**
     * DF-3 / F2-21: {@code memberName} is now <b>optional</b> and no longer identifies
     * the voter — the voter is the authenticated principal.
     *
     * <p>Omit it to cast your own vote (the normal path). Supply it only when a committee
     * secretary or chair is recording a vote <em>on behalf of</em> a named member; that
     * requires {@code ADMIN:APPROVE} and stamps {@code on_behalf = 1}. It was previously
     * {@code @NotBlank} and taken as the voter's identity, which is precisely the defect:
     * any caller could vote as any name. Note the ordering landmine — {@code @Valid} runs
     * <em>before</em> {@code @PreAuthorize}, so a probe of the authorisation behaviour
     * still needs a body that satisfies these constraints.
     */
    public record ResolutionVoteRequest(
            @Size(max = 255) String memberName,
            @Size(max = 120) String memberRole,
            @NotBlank String vote,   // FOR | AGAINST | ABSTAIN | RECUSED
            String rationale
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MeetingResponse(
            Long id, String meetingCode, String externalId,
            Long committeeId, String committeeCode, String committeeNameEn,
            String titleEn, String titleAr,
            LocalDateTime scheduledAt, LocalDateTime startedAt, LocalDateTime endedAt,
            String locationOrLink,
            String agendaJson, String attendeesJson, Short quorumMet,
            String minutesEn, String minutesAr,
            String decisionsJson, String actionItemsJson,
            String chairName, String secretaryName,
            String status, String cancelReason,
            LocalDateTime minutesApprovedAt, String approverName,
            LocalDateTime createdAt, LocalDateTime updatedAt
    ) {
        public static MeetingResponse from(CommitteeMeeting m) {
            return new MeetingResponse(
                    m.getId(), m.getMeetingCode(), m.getExternalId(),
                    m.getCommitteeId(), m.getCommitteeCode(), m.getCommitteeNameEn(),
                    m.getTitleEn(), m.getTitleAr(),
                    m.getScheduledAt(), m.getStartedAt(), m.getEndedAt(),
                    m.getLocationOrLink(),
                    m.getAgendaJson(), m.getAttendeesJson(), m.getQuorumMet(),
                    m.getMinutesEn(), m.getMinutesAr(),
                    m.getDecisionsJson(), m.getActionItemsJson(),
                    m.getChairName(), m.getSecretaryName(),
                    m.getStatus(), m.getCancelReason(),
                    m.getMinutesApprovedAt(), m.getApproverName(),
                    m.getCreatedAt(), m.getUpdatedAt());
        }
    }
}
