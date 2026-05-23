package com.tss.grc.module.integrations.crowdstrike.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of a {@code POST /contain/{hostId}} or {@code POST /lift/{hostId}}
 * call. The host id sits in the URL path; this body carries the
 * justification text the SOC analyst supplied — required for audit
 * trail because NCA ECC-2 expects "who authorised the containment and
 * why" to be recoverable from the audit log.
 *
 * @param reason free-form analyst justification. Stored verbatim in
 *               {@code crowdstrike_action_log.reason_text} and surfaced
 *               in the F7 audit_log message.
 */
public record ContainmentRequest(
    @NotBlank @Size(max = 500) String reason
) {}
