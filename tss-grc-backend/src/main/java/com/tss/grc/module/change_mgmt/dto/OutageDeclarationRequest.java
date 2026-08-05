package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * S8g / CE-8 — declare, amend or withdraw the projected service outage for a change.
 *
 * <p>{@code @Min(1)} mirrors {@code ck_change_pso}'s {@code outage_minutes > 0}, and both exist on
 * purpose: the annotation gives the caller a 400 that explains the rule, the CHECK makes the rule true
 * for a direct UPDATE or a support script. Same layering as the ratification SLA bound (V345), whose
 * javadoc records the reasoning — the rule has to hold at whichever layer the write arrives on.
 *
 * <p>The upper bound is a week of continuous outage. Not arbitrary: a change projecting more than that
 * is not a change window, it is a service withdrawal, and it belongs in BCM rather than in the FSC. The
 * bound exists so the number is a plan rather than a typo — an outage entered in seconds by mistake
 * would otherwise publish "20160 minutes" to the business.
 */
public record OutageDeclarationRequest(

    /** {@code false} withdraws the declaration; the service then clears the duration and services. */
    @NotNull
    Boolean outageExpected,

    @Min(1)
    @Max(10_080)
    Integer outageMinutes,

    @Size(max = 1000)
    String servicesAffected
) { }
