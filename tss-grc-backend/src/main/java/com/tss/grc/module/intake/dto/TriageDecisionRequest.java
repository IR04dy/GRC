package com.tss.grc.module.intake.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Triager decision posted to {@code PUT /api/v1/intake/{id}/triage}.
 *
 * <h3>⚠ What is deliberately NOT on this DTO — F2-172</h3>
 * It used to carry {@code promotedToEntityType} and {@code promotedToRecordId}, and
 * {@code IntakeService.triage()} copied both straight onto the row. The comment said they were
 * "echoed back so the frontend can deeplink to the new register row", but nothing created that row
 * — on either side — so the fields let the client name a record that did not exist, and the intake's
 * permanent history note then quoted the number back ({@code "Promoted to DPIA #482913"}).
 *
 * <p>Both are now derived by the server: the <b>target</b> from the kind's catalogue entry, and the
 * <b>record id</b> from the row the promotion actually wrote. Neither is expressible by a caller,
 * which is the point — a client-supplied identity for a server-side write is the shape of the
 * defect, not an incidental detail of it. {@code IntakeTriageContractTest} fails if either field
 * comes back.
 *
 * <p>({@code spring.jackson.deserialization.fail-on-unknown-properties: false} means an old client
 * still sending them gets them ignored rather than a 400. That is the right degradation here: the
 * fields were never load-bearing, and the response carries the real target and id.)
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class TriageDecisionRequest {
    /** PROMOTE | REJECT | CLOSE. */
    @NotBlank private String decision;
    /** Required for REJECT and CLOSE; on PROMOTE it is appended to the server's promotion note. */
    private String note;
}
