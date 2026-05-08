package com.tss.grc.module.intake.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Submission payload for POST /api/v1/intake. Intentionally matches the
 * shape produced by the IntakeFormPage on the frontend (DD-59) so the
 * client can submit without a translation layer.
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class IntakeSubmissionRequest {
    @NotBlank private String kind;
    @NotBlank private String channel;
    @NotNull  private Boolean submitterIsAnonymous;
    private String  submitterRole;
    private String  submitterName;
    private String  submitterEmail;
    private String  submitterPhone;
    /** Form payload as JSON string. Validated server-side per kind. */
    private String  payload;
    private String  severity;
    private String  locale;
}
