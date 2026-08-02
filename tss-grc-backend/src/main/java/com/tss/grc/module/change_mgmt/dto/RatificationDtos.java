package com.tss.grc.module.change_mgmt.dto;

import com.tss.grc.module.change_mgmt.model.RatificationStates;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A17-D3 request bodies — emergency-change ratification and its per-tenant SLA. */
public final class RatificationDtos {

    private RatificationDtos() { }

    /**
     * The CAB's retrospective decision on an emergency change.
     *
     * <p>Carries the decision and a rationale and <b>nothing about who decided</b>.
     * The ratifier is the authenticated principal, because the segregation-of-duties
     * gate compares that identity against the emergency approver, the raiser and the
     * owner — and a body-supplied name is exactly how F2-24 let a requester approve
     * their own change by naming somebody else.
     */
    public record RatifyRequest(
        @NotBlank
        @Pattern(regexp = "RATIFY|REJECT",
                 message = "decision must be RATIFY or REJECT")
        String decision,

        @Size(max = 4000)
        String notes
    ) { }

    /**
     * Per-tenant SLA update.
     *
     * <p>{@code @Max} mirrors {@code ck_ratification_sla_bounds} in the database. Both
     * exist on purpose: the annotation gives the caller a 400 that explains the rule,
     * the CHECK makes the rule true for a direct UPDATE or a support script. ADR-017
     * D-3 amendment 2 asked "what stops a tenant choosing 365 days?" — the answer has
     * to hold at whichever layer the write arrives on.
     */
    public record RatificationPolicyRequest(
        @Min(RatificationStates.MIN_SLA_BUSINESS_DAYS)
        @Max(RatificationStates.MAX_SLA_BUSINESS_DAYS)
        Integer slaBusinessDays,

        @Size(max = 60)
        String escalationRole
    ) { }
}
