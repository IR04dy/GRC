package com.tss.grc.module.platform.dto;

import java.time.LocalDateTime;

/**
 * A notification as served to its recipient.
 *
 * <p><b>F2-38 (2026-08-06) — this record now exposes what {@code platform.notifications} actually
 * holds, and nothing it does not.</b> As committed it declared {@code severity} and {@code linkUrl},
 * and {@code NotificationService.toResponse} hard-coded <b>null</b> for both because <b>neither is a
 * column on the table</b>. Two fields that could never carry information, on every one of the 445
 * live rows. It also passed null for {@code bodyAr} while {@code message_ar} <i>is</i> a column and
 * <b>42 rows hold real Arabic text</b> — so the mapper discarded Arabic the database had, on a
 * product whose primary language is Arabic.
 *
 * <p><b>Why the two dropped fields were not simply populated.</b> Inventing a severity from
 * {@code notification_type} was rejected: that column carries <b>two vocabularies in one</b> — four
 * of its sixteen values are severities ({@code INFO}, {@code WARNING}, {@code DANGER},
 * {@code NOTIFICATION}, 48 rows between them) and twelve are event types
 * ({@code EVIDENCE_REQUEST_ESCALATION} alone is 162 rows). Deriving a severity would have meant
 * assigning one to 397 rows that never had one. A fabricated field is worse than an absent one,
 * because it reads as data.
 *
 * <p><b>What replaces {@code linkUrl}.</b> {@code entityType} and {@code entityId} are real,
 * populated on <b>all 445 rows</b>, and were exposed by nothing. The recipient's client builds the
 * route from them, which keeps frontend routing knowledge in the frontend — the server never knew
 * the SPA's paths, which is why {@code linkUrl} was null rather than wrong. ⚠ {@code entityType}
 * carries two casings, and a consumer that handles only one will silently produce no link for half
 * the rows: nine values are CamelCase JPA names ({@code EvidenceRequest} 324, {@code WorkflowRequest}
 * 33, {@code CorrectiveAction} 17 …) and eight are SCREAMING_CASE domain names ({@code VENDOR},
 * {@code CHANGE}, {@code POLICY}, {@code KRI} …). Normalising it is a data migration, not a DTO
 * change, so the vocabulary is left as it is and the frontend map is gated on covering all of it.
 */
public record NotificationResponse(
    Long id,
    String titleEn,
    String titleAr,
    String bodyEn,
    String bodyAr,
    /** ⚠ Two vocabularies: severity-shaped for 48 rows, event-shaped for 397. Not a severity. */
    String type,
    Boolean isRead,
    /** ⚠ Mixed casing across 17 values — see the class note before keying a map on this. */
    String entityType,
    Long entityId,
    LocalDateTime createdAt
) {}
