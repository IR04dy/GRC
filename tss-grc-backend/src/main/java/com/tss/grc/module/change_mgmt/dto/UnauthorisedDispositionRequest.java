package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * S8f / CE-11 — an investigator's recorded outcome for one reconciliation finding.
 *
 * <p><b>Carries no identity and no timestamp.</b> {@code disposedBy*} and {@code disposedAt} come
 * from the authenticated principal and the server clock, which is what makes V355's
 * {@code ck_ucd_disposed} a control rather than a decoration: the constraint can test that the
 * authority fields <em>agree</em>, but it cannot test who supplied them. A body-supplied name is how
 * F2-24 let a requester approve their own change by naming somebody else, and S8e refused the same
 * shape when {@code ChangeModelRequest} was denied {@code status} and {@code authorised*}.
 *
 * <p>The vocabularies are validated in the service against
 * {@code UnauthorisedChangeDisposition.STATES} and {@code UnauthorisedChangeFinding.REASON_CODES}
 * rather than by a {@code @Pattern} here, so one published list governs the DTO, the service and
 * V355's {@code ck_ucd_state} — three copies of a regex is how they drift apart.
 */
public record UnauthorisedDispositionRequest(

    /** {@code ACCEPTED_RISK} · {@code RETROSPECTIVELY_AUTHORISED} · {@code REVERSED} · {@code FALSE_POSITIVE}. */
    @NotBlank
    String state,

    /** Why. Optional — a state with no explanation is still a recorded, attributed judgement. */
    @Size(max = 2000)
    String note
) { }
