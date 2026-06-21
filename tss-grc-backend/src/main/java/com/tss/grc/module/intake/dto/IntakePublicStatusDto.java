package com.tss.grc.module.intake.dto;

import com.tss.grc.module.intake.entity.IntakeRecord;

import java.time.LocalDateTime;

/**
 * Minimal, PII-free projection returned by the PUBLIC (unauthenticated)
 * case-tracker endpoint {@code GET /api/v1/intake/track/{code}} (SEC-FIN-001).
 *
 * <p>A reference code is a weak, often-enumerable bearer, so the public lookup
 * must expose ONLY what the submitter needs to see their own progress. It
 * deliberately omits submitter identity (name/email/phone), the raw form
 * payload, the assignee, the rejection reason and the full event history — all
 * of which the full {@link IntakeRecordDto} (authenticated triager view) carries.
 */
public record IntakePublicStatusDto(
    String referenceCode,
    String kind,
    String status,
    LocalDateTime acknowledgedAt,
    LocalDateTime slaDueAt
) {
    public static IntakePublicStatusDto from(IntakeRecord r) {
        return new IntakePublicStatusDto(
            r.getReferenceCode(),
            r.getKind(),
            r.getStatus(),
            r.getAcknowledgedAt(),
            r.getSlaDueAt()
        );
    }
}
