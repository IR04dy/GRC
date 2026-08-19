package com.tss.grc.module.change_mgmt.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * F2-248 — a comment being added to a change request.
 *
 * <h2>Note what is absent: the author</h2>
 *
 * <p>{@code POST /api/v1/changes/{id}/comments} used to bind {@link
 * com.tss.grc.module.change_mgmt.entity.ChangeComment} itself, so a caller supplied
 * {@code authorName} and {@code authorId} and the service wrote whatever arrived. Probed live as
 * {@code ranalyst} (a {@code RISK_ANALYST}, the least-privileged holder of {@code CHANGE:WRITE}):
 * a comment on a CAB-approved change stored {@code author_name = 'so_officer'} — the CISO — with
 * HTTP 201, and {@code authorId: 8} bound straight onto a column that is NULL on <b>45 of 45</b>
 * dev rows and <b>46 of 46</b> prod rows, i.e. one nothing had ever written.
 *
 * <p><b>Whoever wrote a comment IS the caller.</b> Unlike a condition's owner or a change's
 * requesting party — both of which are legitimately a team label somebody types (see
 * {@link ChangeConditionRequest#ownerName} and {@code ChangeSoD}'s measurement of 0-of-28
 * people-labels resolving to a user) — authorship is not an assignment and has no second
 * candidate. So the fields are not validated here, they are <b>absent</b>: a field the bound type
 * does not declare can never arrive, which is the strongest closure available (F2-233) and the one
 * {@code MassAssignmentRatchetTest} already demanded of {@link ChangeConditionRequest} by name —
 * <i>"a scrub is a list of fields somebody remembered, a DTO is the set of fields that exist."</i>
 *
 * <p>⚠ The old default was {@code if (body.getAuthorName() == null) body.setAuthorName("Unknown")}
 * — a <b>default, not a guard</b> (F2-218): it discarded nothing the caller sent and supplied a
 * label only when the caller stayed silent. The frontend's half was
 * {@code getUser()?.username ?? 'You'}, under a comment reading <i>"CH-8: attribute the comment to
 * the signed-in user, not a hardcoded 'You'"</i> — and the prod register holds a row whose author
 * is the literal string {@code 'You'}, so the fallback the comment claimed to have removed had
 * fired for real.
 *
 * <p>No {@code @Size} on {@code body}: the column is {@code NVARCHAR(MAX)}, so the schema
 * deliberately leaves it unbounded and a cap here would be a product change rather than a mirror
 * (F2-185b).
 *
 * <p>Ordering landmine: {@code @Valid} runs <b>before</b> {@code @PreAuthorize}, so an
 * authorisation probe against this endpoint still needs a body that satisfies this constraint.
 */
public record ChangeCommentRequest(

        /** What the commenter has to say. The only thing a caller decides here. */
        @NotBlank String body
) { }
