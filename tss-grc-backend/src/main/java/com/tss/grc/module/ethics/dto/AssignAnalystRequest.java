package com.tss.grc.module.ethics.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * F2-249 — who an AML alert is being assigned to.
 *
 * <p>Exactly one of the two may be supplied, and they are not interchangeable:
 *
 * <ul>
 *   <li>{@code analystUserId} — a <b>person</b>. The server resolves it against the caller's own
 *       tenant and <b>derives</b> {@code analyst_name} from the row it resolved, so the label and
 *       the id cannot disagree. This is the path the register's picker uses.</li>
 *   <li>{@code analystName} — an <b>organisational unit</b>. Live rows hold
 *       {@code 'Financial Crime Unit'} and {@code 'Head of FCC'}; those are real assignments to a
 *       team rather than to an individual, and the row records that by leaving
 *       {@code analyst_id} NULL, which the register discloses.</li>
 * </ul>
 *
 * <p>⚠ The label is deliberately <b>not</b> refused, and that is a decision the schema makes
 * rather than a preference: {@code analyst_id} is nullable <em>beside</em> a nullable
 * {@code analyst_name}, which states that an unresolvable assignee is representable (F2-246's
 * rule). Where a table declares only an id — F2-244's {@code coi_recusal_event} — the opposite
 * holds and the reference is required. What is refused is a label that <em>contradicts</em> a
 * supplied id, because then the row asserts two different things about one assignee
 * (F2-231/F2-234's echo-versus-change rule).
 *
 * @param analystUserId the assignee, when they are a platform user of this tenant
 * @param analystName   the assignee as free text, for a team or an external party
 */
public record AssignAnalystRequest(
        @Positive Long analystUserId,
        @Size(max = 255) String analystName
) { }
