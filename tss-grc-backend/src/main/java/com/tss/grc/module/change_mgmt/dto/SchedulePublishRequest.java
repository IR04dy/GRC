package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * S8g / CE-8 — publish the forward schedule for a window.
 *
 * <p>Carries the window and a covering note and <b>nothing about who published</b>: the publisher and
 * the moment come from the authenticated principal and the server clock, which is what makes V356's
 * {@code ck_csp_published} a control rather than a decoration. Same reasoning as
 * {@code UnauthorisedDispositionRequest} and {@code ChangeModelRequest}.
 *
 * <p>Also carries no entry list. The snapshot is taken by the server from the live schedule, because a
 * caller-supplied list would let the published document differ from what the platform actually holds —
 * and the whole value of the digest is that the two cannot diverge.
 */
public record SchedulePublishRequest(

    @NotNull
    LocalDateTime from,

    @NotNull
    LocalDateTime to,

    /** Covering note — "issued to service owners ahead of the Q4 freeze", and so on. */
    @Size(max = 2000)
    String notes
) { }
