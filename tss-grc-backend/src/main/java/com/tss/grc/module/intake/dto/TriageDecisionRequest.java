package com.tss.grc.module.intake.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Triager decision posted to PUT /api/v1/intake/{id}/triage.
 * Mirror of the frontend triage() call's `decision` parameter and `extra`
 * options. The promoted entity-type / record-id are echoed back so the
 * frontend can deeplink to the new register row.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class TriageDecisionRequest {
    /** PROMOTE | REJECT | CLOSE. */
    @NotBlank private String decision;
    /** Required for REJECT and CLOSE; optional explanatory note for PROMOTE. */
    private String note;
    /** Set on PROMOTE to record the downstream register row. */
    private String promotedToEntityType;
    private String promotedToRecordId;
}
