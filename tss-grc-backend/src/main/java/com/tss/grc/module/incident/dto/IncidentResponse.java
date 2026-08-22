package com.tss.grc.module.incident.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record IncidentResponse(
    Long id,
    String title,
    String titleAr,
    String severity,
    String category,
    String status,
    String assignee,
    LocalDate discoveryDate,
    LocalDate resolutionDate,
    String description,
    String timelineJson,
    String lessonsLearned,
    String lessonsLearnedAr,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,

    // ── PDPL classification (F2-281) ────────────────────────────────────────
    // Real columns on cyber.incidents, written by the cyber -> PDPL bridge and
    // declared by nothing until F2-281 -- so an incident classified as
    // affecting personal data looked identical to one that was not, on every
    // surface the platform has. Read-only here: the bridge owns the writes.
    /** True once the bridge has classified this incident as affecting personal data. */
    boolean affectsPersonalData,
    /** Estimated affected data subjects. Null means NOT ASSESSED -- never zero. */
    Integer dataSubjectsAffectedEst,
    /** Comma-separated categories of personal data, e.g. "EMAIL,NATIONAL_ID". */
    String dataCategoriesAffected,
    /** The DPO's narrative assessment recorded at classification. */
    String personalDataPiiAssessment,
    /** The privacy.data_breaches row the bridge auto-created, if any. */
    Long linkedDataBreachId,
    /** That breach's external id -- the reference an SDAIA filing cites. */
    String linkedDataBreachCode,
    LocalDateTime piiClassifiedAt,
    Long piiClassifiedBy
) {}
