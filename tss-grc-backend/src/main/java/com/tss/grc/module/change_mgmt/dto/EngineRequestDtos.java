package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * S8h / CE-10 — request bodies for routing change work through the platform workflow engine.
 *
 * <p><b>Narrow on purpose.</b> The engine's own {@code CreateRequest} has 28 fields including
 * multi-step recipient lists, branch rules, committee expansion and a QUORUM threshold. A change task
 * has one owner and one due date, so exposing routing modes the record cannot express would be a
 * surface with no subject — and every extra field is one more thing the change UI must keep in step
 * with the engine's validation.
 *
 * <p>⚠ There is no {@code requestType = APPROVAL} path and no way to add one from here:
 * {@code WorkflowPort.raise} refuses the type, because change authorisation has exactly two routes
 * and an engine request is neither.
 */
public final class EngineRequestDtos {

    private EngineRequestDtos() { }

    /**
     * Raise an engine request against a change or one of its tasks.
     *
     * @param addresseeType USER / ROLE / DEPARTMENT / COMMITTEE — the engine's own categories, minus
     *                      EXTERNAL (a task owned by an email address is a different feature)
     * @param addresseeId   the user / department / committee id; null for ROLE
     * @param addresseeKey  the role name; null for the id-addressed kinds
     * @param slaHours      bounded at 720 (30 days) — an SLA longer than a month is a due date with
     *                      extra steps, and the engine's reminder ladder cannot express it usefully
     */
    public record RaiseEngineRequest(
        @NotBlank @Size(max = 40) String requestType,
        @Size(max = 300) String titleEn,
        @Size(max = 300) String titleAr,
        @Size(max = 4000) String description,
        @NotBlank @Size(max = 20) String addresseeType,
        Long addresseeId,
        @Size(max = 120) String addresseeKey,
        @Size(max = 200) String addresseeLabel,
        LocalDate dueDate,
        @Min(1) @Max(720) Integer slaHours,
        @Size(max = 20) String priority,
        @Size(max = 60) String escalationRole
    ) { }
}
