package com.tss.grc.module.ethics.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * F2-248 — an action being recorded on an ethics case.
 *
 * <h2>Note what is absent: the actor</h2>
 *
 * <p>{@code POST /api/v1/ethics/cases/{id}/actions} used to bind {@link
 * com.tss.grc.module.ethics.entity.CaseAction} itself, and {@code addAction} touched neither
 * {@code actorName} nor {@code actorId} — they were stored exactly as they arrived. This is the
 * action log of a whistleblowing or misconduct investigation: interviews held, evidence requested,
 * findings made, HR referrals, closure.
 *
 * <p>🔑 <b>The clock was already the server's and the actor was the caller's.</b>
 * {@code action_at} is {@code insertable = false}, so the <b>database</b> supplies it and the row
 * has always recorded <i>when</i> authoritatively — which is precisely F2-223's split attribution,
 * with the server-clock half arriving through the one filling mechanism a setter-matching scan
 * cannot see (F2-226's fourth mechanism). Nothing detected it for that reason.
 *
 * <p><b>Measured, and the symptom is the opposite of its twin's.</b> Where the change-comment
 * thread's frontend sent a <i>claim</i>, {@code EthicsPage} sends
 * {@code { actionType, title, description }} and <b>no actor at all</b> — so every action recorded
 * through the platform's own screen stored {@code actor_name = NULL} and {@code actor_id = NULL}.
 * The 15 rows in dev and prod are seeded, all 15 naming an office
 * ({@code 'HR Investigations Lead'}, {@code 'Compliance / الالتزام'}) that resolves to no
 * {@code iam.users} row. <b>Both halves of one defect: a register that does not own WHO either
 * records a claim or records nothing, and neither is an attribution.</b>
 *
 * <p>So the actor fields are <b>absent</b> here rather than validated — a field the bound type does
 * not declare can never arrive (F2-233), and the service now takes both halves from the
 * authenticated principal.
 *
 * <p>Caps mirror the columns exactly, which is the only case where enabling one cannot break a
 * working request (F2-185b): {@code action_type} is {@code NVARCHAR(40)} and {@code title} is
 * {@code NVARCHAR(500)}. {@code description} is {@code NVARCHAR(MAX)} — deliberately unbounded, so
 * it carries no cap.
 *
 * <p>⚠ {@code action_type} is <b>not</b> narrowed to a vocabulary. {@code ethics.case_actions} has
 * <b>zero CHECK constraints</b> and holds nine distinct values over 15 rows; nothing refuses an
 * unrecognised one today, so a {@code require} here could break a request that works (F2-182), and
 * closing it means a migration whose scope is the writers it closes (F2-183). Filed, not guessed.
 */
public record CaseActionRequest(

        /** What kind of action this is — INTERVIEW, FINDING, HR_REFERRAL, CLOSURE and so on. */
        @NotBlank @Size(max = 40) String actionType,

        /** One-line summary of the action, as it appears on the case timeline. */
        @NotBlank @Size(max = 500) String title,

        /** The detail. Unbounded, matching the {@code NVARCHAR(MAX)} column. */
        String description,

        /**
         * Whether the entry is restricted. Omitted means the entity's own default of {@code true} —
         * an ethics case action is confidential unless somebody says otherwise, and that default
         * belongs on the create path rather than being re-decided here.
         */
        Boolean isConfidential
) { }
